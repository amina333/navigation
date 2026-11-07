#!/bin/bash
# ============================================================
#  SkyGuide — reconstruction du cluster Kubernetes (etcd corrompu)
#
#  Usage (sur la VM) :   sudo bash deploy/scripts/rebuild-cluster.sh
#
#  1. Sauvegarde : etcd (corrompu) + données PostgreSQL et MinIO
#  2. kubeadm reset puis kubeadm init (mêmes version et réseau qu'avant)
#  3. Calico v3.28.0, stockage local-path, (Tekton), ArgoCD
#  4. Rebranche les ANCIENNES données PostgreSQL / MinIO (aucune perte applicative)
#  5. Secret app-secrets, application ArgoCD, build + déploiement de l'application
# ============================================================
set -euo pipefail

POD_CIDR="192.168.0.0/16"            # identique à l'installation d'origine (Calico)
CALICO_VERSION="v3.28.0"
LOCAL_PATH_VERSION="v0.0.30"
ARGOCD_NODEPORT=30793                 # même adresse ArgoCD qu'avant : https://<IP>:30793
INSTALL_TEKTON="${INSTALL_TEKTON:-true}"
PV_ROOT="/opt/local-path-provisioner"

G='\033[0;32m'; Y='\033[1;33m'; R='\033[0;31m'; C='\033[0;36m'; B='\033[1m'; N='\033[0m'
step() { echo -e "\n${B}${C}▶ $1${N}"; }
ok()   { echo -e "  ${G}✔ $1${N}"; }
warn() { echo -e "  ${Y}! $1${N}"; }
fail() { echo -e "  ${R}✘ $1${N}"; exit 1; }

[[ $EUID -eq 0 ]] || fail "à lancer avec sudo : sudo bash deploy/scripts/rebuild-cluster.sh"
REAL_USER="${SUDO_USER:-amina}"
REAL_HOME=$(getent passwd "$REAL_USER" | cut -d: -f6)
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
[[ -f "$REPO/deploy/secrets.env" ]] || fail "$REPO/deploy/secrets.env introuvable"
NODE_IP=$(hostname -I | awk '{print $1}')
TS=$(date +%Y%m%d-%H%M%S)
BACKUP="$REAL_HOME/k8s-recovery-$TS"
START=$(date +%s)
export KUBECONFIG=/etc/kubernetes/admin.conf

echo -e "${B}Reconstruction du cluster — nœud ${NODE_IP} — dépôt ${REPO}${N}"

# ── 1. Sauvegardes ────────────────────────────────────────
step "1/9 Sauvegardes (avant toute modification)"
mkdir -p "$BACKUP"
if [[ -d /var/lib/etcd ]]; then
  tar -czf "$BACKUP/etcd-corrompu.tar.gz" -C /var/lib etcd && ok "etcd sauvegardé"
fi
PG_DIR=$(ls -dt "$PV_ROOT"/pvc-*_skyguide_postgres-pvc 2>/dev/null | head -1 || true)
MINIO_DIR=$(ls -dt "$PV_ROOT"/pvc-*_skyguide_minio-pvc 2>/dev/null | head -1 || true)
[[ -n "$PG_DIR" ]] || fail "dossier de données PostgreSQL introuvable dans $PV_ROOT"
[[ -n "$MINIO_DIR" ]] || warn "dossier MinIO introuvable (nouveau stockage vide)"
tar -czf "$BACKUP/postgres-data.tar.gz" -C "$(dirname "$PG_DIR")" "$(basename "$PG_DIR")" && ok "PostgreSQL sauvegardé ($(du -sh "$PG_DIR" | cut -f1))"
[[ -n "$MINIO_DIR" ]] && tar -czf "$BACKUP/minio-data.tar.gz" -C "$(dirname "$MINIO_DIR")" "$(basename "$MINIO_DIR")" && ok "MinIO sauvegardé ($(du -sh "$MINIO_DIR" | cut -f1))"
cp -a /etc/kubernetes "$BACKUP/etc-kubernetes" 2>/dev/null || true
printf "PG_DIR=%s\nMINIO_DIR=%s\n" "$PG_DIR" "$MINIO_DIR" > "$BACKUP/volumes.txt"
chown -R "$REAL_USER:$REAL_USER" "$BACKUP"
ok "sauvegardes dans $BACKUP"

# ── 2. Reset ──────────────────────────────────────────────
step "2/9 Réinitialisation de Kubernetes (kubeadm reset)"
swapoff -a || true
kubeadm reset -f > "$BACKUP/kubeadm-reset.log" 2>&1 || true
rm -rf /etc/cni/net.d /var/lib/calico /var/lib/etcd
iptables -F && iptables -t nat -F && iptables -t mangle -F && iptables -X || true
rm -f "$REAL_HOME/.kube/config" /root/.kube/config
systemctl restart containerd
ok "ancien cluster supprimé (images containerd et données conservées)"

# ── 3. Init ───────────────────────────────────────────────
step "3/9 Initialisation (kubeadm init)"
kubeadm init --pod-network-cidr="$POD_CIDR" --apiserver-advertise-address="$NODE_IP" > "$BACKUP/kubeadm-init.log" 2>&1 \
  || { tail -30 "$BACKUP/kubeadm-init.log"; fail "kubeadm init a échoué (log : $BACKUP/kubeadm-init.log)"; }
mkdir -p "$REAL_HOME/.kube" /root/.kube
cp /etc/kubernetes/admin.conf "$REAL_HOME/.kube/config"; chown "$REAL_USER:$REAL_USER" "$REAL_HOME/.kube/config"
cp /etc/kubernetes/admin.conf /root/.kube/config
kubectl taint nodes --all node-role.kubernetes.io/control-plane- >/dev/null 2>&1 || true
ok "control-plane prêt, nœud ouvert aux pods"

# ── 4. Réseau ─────────────────────────────────────────────
step "4/9 Réseau Calico $CALICO_VERSION"
kubectl create -f "https://raw.githubusercontent.com/projectcalico/calico/${CALICO_VERSION}/manifests/tigera-operator.yaml" >/dev/null
curl -fsSL "https://raw.githubusercontent.com/projectcalico/calico/${CALICO_VERSION}/manifests/custom-resources.yaml" -o /tmp/calico-cr.yaml
sed -i "s#cidr: 192.168.0.0/16#cidr: ${POD_CIDR}#" /tmp/calico-cr.yaml
for i in $(seq 1 30); do kubectl get crd installations.operator.tigera.io >/dev/null 2>&1 && break; sleep 5; done
kubectl create -f /tmp/calico-cr.yaml >/dev/null
kubectl wait --for=condition=Ready node --all --timeout=420s >/dev/null && ok "nœud Ready" || warn "nœud pas encore Ready, on continue"

# ── 5. Stockage + volumes existants ───────────────────────
step "5/9 Stockage local-path et rebranchement des données"
kubectl apply -f "https://raw.githubusercontent.com/rancher/local-path-provisioner/${LOCAL_PATH_VERSION}/deploy/local-path-storage.yaml" >/dev/null
kubectl patch storageclass local-path -p '{"metadata":{"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}' >/dev/null
kubectl create namespace skyguide --dry-run=client -o yaml | kubectl apply -f - >/dev/null

make_pv() { # $1=nom PVC  $2=dossier
  # PV : apply ; PVC : create (sans annotation last-applied, pour qu'ArgoCD ne tente pas
  # de retirer volumeName/storageClassName, champs immuables)
  cat <<EOF | kubectl apply -f - >/dev/null
apiVersion: v1
kind: PersistentVolume
metadata:
  name: skyguide-$1-restored
  labels: { app.kubernetes.io/part-of: skyguide }
spec:
  capacity: { storage: 5Gi }
  accessModes: [ReadWriteOnce]
  persistentVolumeReclaimPolicy: Retain
  storageClassName: local-path
  hostPath: { path: $2, type: Directory }
  claimRef: { namespace: skyguide, name: $1 }
EOF
  kubectl -n skyguide get pvc "$1" >/dev/null 2>&1 && return 0
  cat <<EOF | kubectl create -f - >/dev/null
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: $1
  namespace: skyguide
spec:
  accessModes: [ReadWriteOnce]
  storageClassName: local-path
  volumeName: skyguide-$1-restored
  resources: { requests: { storage: 5Gi } }
EOF
}
make_pv postgres-pvc "$PG_DIR" && ok "postgres-pvc → $(basename "$PG_DIR")"
[[ -n "$MINIO_DIR" ]] && make_pv minio-pvc "$MINIO_DIR" && ok "minio-pvc → $(basename "$MINIO_DIR")"

# ── 6. Secrets ────────────────────────────────────────────
step "6/9 Secret app-secrets"
kubectl -n skyguide create secret generic app-secrets --from-env-file="$REPO/deploy/secrets.env" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null && ok "app-secrets créé depuis deploy/secrets.env"

# ── 7. Tekton (facultatif) ────────────────────────────────
step "7/9 Tekton"
if [[ "$INSTALL_TEKTON" == true ]]; then
  kubectl apply -f https://storage.googleapis.com/tekton-releases/pipeline/latest/release.yaml >/dev/null 2>&1 \
    && kubectl apply -f https://storage.googleapis.com/tekton-releases/dashboard/latest/release.yaml >/dev/null 2>&1 \
    && ok "Tekton Pipelines + Dashboard installés" || warn "Tekton non installé (facultatif)"
  for f in rbac.yaml tasks/kustomize-deploy.yaml pipeline.yaml; do
    [[ -f "$REPO/deploy/tekton/$f" ]] && kubectl apply -f "$REPO/deploy/tekton/$f" >/dev/null 2>&1 || true
  done
else
  warn "ignoré (INSTALL_TEKTON=false)"
fi

# ── 8. ArgoCD ─────────────────────────────────────────────
step "8/9 ArgoCD"
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f - >/dev/null
kubectl apply -n argocd --server-side --force-conflicts -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml >/dev/null
kubectl -n argocd patch svc argocd-server --type merge -p \
  "{\"spec\":{\"type\":\"NodePort\",\"ports\":[{\"name\":\"http\",\"port\":80,\"protocol\":\"TCP\",\"targetPort\":8080},{\"name\":\"https\",\"port\":443,\"protocol\":\"TCP\",\"targetPort\":8080,\"nodePort\":${ARGOCD_NODEPORT}}]}}" >/dev/null \
  && ok "ArgoCD exposé sur https://${NODE_IP}:${ARGOCD_NODEPORT}"
kubectl wait --for=condition=Ready pods --all -n argocd --timeout=420s >/dev/null || warn "certains pods ArgoCD ne sont pas encore prêts"
kubectl apply -f "$REPO/deploy/argocd/application.yaml" >/dev/null && ok "application ArgoCD « skyguide » créée"

# ── 9. Application ────────────────────────────────────────
step "9/9 Build et déploiement de l'application (update-vm.sh)"
sudo -u "$REAL_USER" -H bash -c "cd '$REPO' && bash deploy/scripts/update-vm.sh" || warn "update-vm.sh a signalé une erreur (voir ci-dessus)"

ARGO_PWD=$(kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" 2>/dev/null | base64 -d || true)
APP_PORT=$(kubectl -n skyguide get svc skyguide-frontend -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null || true)
echo ""
kubectl get pods -A --no-headers | awk '{printf "  %-18s %-48s %s\n",$1,$2,$4}'
echo ""
ok "Terminé en $(( ($(date +%s) - START) / 60 )) min"
echo -e "  Application : ${B}http://${NODE_IP}:${APP_PORT}${N}"
echo -e "  ArgoCD      : ${B}https://${NODE_IP}:${ARGOCD_NODEPORT}${N}  (admin / ${ARGO_PWD:-voir argocd-initial-admin-secret})"
echo -e "  Sauvegardes : ${BACKUP}"
