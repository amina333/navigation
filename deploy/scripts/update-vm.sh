#!/bin/bash
# ============================================================
#  SkyGuide — mise à jour de la VM avec le dernier code GitHub
#
#  Usage (sur la VM, depuis le dossier du dépôt) :
#     sudo -v && bash deploy/scripts/update-vm.sh            # tag v6 par défaut
#     sudo -v && bash deploy/scripts/update-vm.sh v7         # autre tag
#
#  Étapes : git pull → secret app-secrets (dont SMTP) → build backend + frontend
#           (buildah) → import containerd → redéploiement → vérifications.
#  Le tag doit correspondre à deploy/k8s/overlays/dev/kustomization.yaml (ArgoCD).
# ============================================================
set -euo pipefail

TAG="${1:-v6}"
NS="skyguide"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

G='\033[0;32m'; Y='\033[1;33m'; R='\033[0;31m'; C='\033[0;36m'; B='\033[1m'; N='\033[0m'
step() { echo -e "\n${B}${C}▶ $1${N}"; }
ok()   { echo -e "  ${G}✔ $1${N}"; }
warn() { echo -e "  ${Y}! $1${N}"; }
fail() { echo -e "  ${R}✘ $1${N}"; exit 1; }

START=$(date +%s)

# ── 1. Code ────────────────────────────────────────────────
step "1/6 Récupération du code (git pull)"
git fetch --quiet origin
git pull --ff-only origin main || fail "git pull impossible (modifications locales ?) — vérifiez 'git status'"
ok "code à jour : $(git log --oneline -1)"

EXPECTED_TAG=$(grep -A2 "name: skyguide-backend" deploy/k8s/overlays/dev/kustomization.yaml | grep newTag | awk '{print $2}' || true)
if [ -n "$EXPECTED_TAG" ] && [ "$EXPECTED_TAG" != "$TAG" ]; then
  warn "kustomization.yaml attend le tag '$EXPECTED_TAG' — utilisation de ce tag"
  TAG="$EXPECTED_TAG"
fi

# ── 2. Secrets ─────────────────────────────────────────────
step "2/6 Secret app-secrets"
if [ -f deploy/secrets.env ]; then
  for key in MAIL_USERNAME MAIL_PASSWORD MAIL_FROM FRONTEND_URL; do
    grep -q "^${key}=" deploy/secrets.env || warn "$key absent de deploy/secrets.env (voir deploy/secrets.env.example)"
  done
  kubectl -n "$NS" create secret generic app-secrets --from-env-file=deploy/secrets.env \
    --dry-run=client -o yaml | kubectl apply -f - >/dev/null
  ok "app-secrets mis à jour depuis deploy/secrets.env"
else
  warn "deploy/secrets.env introuvable — secret existant conservé (e-mails : copiez deploy/secrets.env.example)"
fi

# ── 3/4. Images ────────────────────────────────────────────
build_and_import() {
  local name="$1" dockerfile="$2"
  local ref="${name}:${TAG}"
  local tar; tar=$(mktemp -u "/tmp/${name}-${TAG}.XXXXXX.tar")
  echo -e "  ${C}buildah build ${ref}${N} (plusieurs minutes la première fois)"
  sudo buildah bud --layers -f "$dockerfile" -t "$ref" . > "/tmp/build-${name}.log" 2>&1 \
    || { tail -30 "/tmp/build-${name}.log"; fail "échec du build de ${ref} (log : /tmp/build-${name}.log)"; }
  sudo buildah push "$ref" "docker-archive:${tar}:${ref}" > /dev/null 2>&1 || fail "export de ${ref} impossible"
  sudo ctr -n k8s.io images import --no-unpack "$tar" > /dev/null || fail "import containerd de ${ref} impossible"
  sudo rm -f "$tar" || true
  sudo ctr -n k8s.io images ls -q | grep -q "${name}:${TAG}" && ok "${ref} importée dans containerd"
}

step "3/6 Image backend (Spring Boot)"
build_and_import skyguide-backend deploy/docker/Dockerfile.backend

step "4/6 Image frontend (Angular + Nginx)"
build_and_import skyguide-frontend deploy/docker/Dockerfile.frontend

# ── 5. Déploiement ────────────────────────────────────────
step "5/6 Déploiement"
if kubectl get application skyguide -n argocd >/dev/null 2>&1; then
  kubectl -n argocd annotate application skyguide argocd.argoproj.io/refresh=hard --overwrite >/dev/null
  ok "ArgoCD : synchronisation demandée (tag ${TAG} depuis GitHub)"
else
  kustomize build deploy/k8s/overlays/dev | kubectl apply -f - >/dev/null
  ok "manifests appliqués (kustomize)"
fi
sleep 5
# Les pods en ErrImageNeverPull (image absente avant le build) repartent avec l'image importée
kubectl -n "$NS" rollout restart deployment/skyguide-backend deployment/skyguide-frontend >/dev/null
kubectl -n "$NS" rollout status deployment/skyguide-backend --timeout=300s || warn "backend pas encore prêt"
kubectl -n "$NS" rollout status deployment/skyguide-frontend --timeout=180s || warn "frontend pas encore prêt"

# ── 6. Vérifications ──────────────────────────────────────
step "6/6 Vérifications"
kubectl -n "$NS" get pods -o wide
echo ""
BACKEND_POD=$(kubectl -n "$NS" get pods -l app=skyguide-backend -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)
if [ -n "$BACKEND_POD" ]; then
  kubectl -n "$NS" logs "$BACKEND_POD" --tail=400 2>/dev/null | grep -E "Started NavigationApplication|Mail —|\[MAIL\]|SkyAI —|ERROR" | tail -8 || true
fi

NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')
NODE_PORT=$(kubectl -n "$NS" get svc skyguide-frontend -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null || true)
echo ""
ok "Terminé en $(( $(date +%s) - START ))s — application : http://<IP publique de la VM>:${NODE_PORT}  (interne : http://${NODE_IP}:${NODE_PORT})"
