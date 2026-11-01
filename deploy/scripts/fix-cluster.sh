#!/bin/bash
# ============================================================
# SkyGuide - cluster-side fixes so ArgoCD can sync (run on the VM)
# Usage (from the repo root):  bash deploy/scripts/fix-cluster.sh
# ============================================================
set -euo pipefail

cd "$(dirname "$0")/../.."          # repo root
NS=skyguide
SECRETS_FILE=deploy/secrets.env

say()  { echo -e "\n\033[1;36m==> $*\033[0m"; }
ok()   { echo -e "  \033[0;32m✔ $*\033[0m"; }
fail() { echo -e "  \033[0;31m✘ $*\033[0m"; exit 1; }

[ -f "$SECRETS_FILE" ] || fail "$SECRETS_FILE not found. Run: cp deploy/secrets.env.example $SECRETS_FILE and fill it in."
grep -q 'CHANGE-ME' deploy/argocd/application.yaml && fail "Set your repoURL in deploy/argocd/application.yaml first."

# ---------- 1. Validate the overlay (same command ArgoCD runs) ----------
say "1/6 Validating kustomize overlay"
kubectl kustomize deploy/k8s/overlays/dev > /dev/null && ok "overlays/dev builds"

# ---------- 2. Default StorageClass (kubeadm has none) ----------
say "2/6 Checking default StorageClass"
if kubectl get sc -o jsonpath='{range .items[*]}{.metadata.annotations.storageclass\.kubernetes\.io/is-default-class}{"\n"}{end}' | grep -q true; then
  ok "default StorageClass already present"
else
  kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/v0.0.30/deploy/local-path-storage.yaml
  kubectl -n local-path-storage rollout status deploy/local-path-provisioner --timeout=120s
  kubectl patch storageclass local-path -p '{"metadata":{"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}'
  ok "local-path installed and set as default"
fi

# ---------- 3. Single-node cluster: allow pods on the control plane ----------
say "3/6 Checking control-plane taint"
if [ "$(kubectl get nodes --no-headers | wc -l)" -eq 1 ]; then
  kubectl taint nodes --all node-role.kubernetes.io/control-plane- 2>/dev/null || true
  ok "single node is schedulable"
else
  ok "multi-node cluster, nothing to do"
fi

# ---------- 4. Build images and import them where kubelet looks (k8s.io) ----------
say "4/6 Building images and importing into containerd (namespace k8s.io)"
command -v buildah >/dev/null || { sudo apt-get update -qq && sudo apt-get install -y -qq buildah; }
for app in backend frontend; do
  ref="docker.io/library/skyguide-$app:latest"   # what kubelet resolves "skyguide-$app:latest" to
  tar="/tmp/skyguide-$app.tar"
  echo "  building skyguide-$app ..."
  sudo buildah bud -f "deploy/docker/Dockerfile.$app" -t "$ref" .
  sudo rm -f "$tar"
  sudo buildah push "$ref" "docker-archive:$tar:$ref"
  sudo ctr -n k8s.io images import "$tar"
  sudo rm -f "$tar"
  ok "skyguide-$app imported"
done
sudo ctr -n k8s.io images ls | grep skyguide || fail "images not visible in containerd k8s.io namespace"

# ---------- 5. Clean old (dev- prefixed / broken) resources + create the Secret ----------
say "5/6 Resetting namespace $NS and creating app-secrets"
if kubectl get ns "$NS" >/dev/null 2>&1; then
  echo "  deleting namespace $NS (old dev-* resources, broken Postgres PVC) ..."
  kubectl delete ns "$NS" --wait=true --timeout=180s
fi
kubectl create ns "$NS"
kubectl -n "$NS" create secret generic app-secrets --from-env-file="$SECRETS_FILE"
ok "namespace and secret ready"

# ---------- 6. (Re)create the ArgoCD Application and force a hard refresh ----------
say "6/6 Applying ArgoCD Application"
kubectl apply -f deploy/argocd/application.yaml
kubectl -n argocd annotate application skyguide argocd.argoproj.io/refresh=hard --overwrite
ok "ArgoCD application applied and hard-refreshed"

echo -e "\nWatch it come up:\n  kubectl get pods -n $NS -w\n"
