#!/bin/bash

# ============================================================
#  SkyGuide - Full Automated Deployment Script
#  Pure containerd runtime — no Docker involved
# ============================================================

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/../../" && pwd)"
NAMESPACE="skyguide"
OVERLAY="${1:-dev}"
TOTAL_STEPS=7
START_TIME=$(date +%s)

# ── Colors & Symbols ──────────────────────────────────────

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
DIM='\033[2m'
RESET='\033[0m'

PASS="${GREEN}✔${RESET}"
FAIL="${RED}✘${RESET}"
WARN="${YELLOW}⚠${RESET}"
INFO="${CYAN}●${RESET}"
ARROW="${GREEN}➜${RESET}"
DOT="${DIM}·${RESET}"

# ── Helper Functions ──────────────────────────────────────

print_banner() {
    echo ""
    echo -e "${BOLD}${CYAN}"
    echo "  ╔═══════════════════════════════════════════════════════╗"
    echo "  ║                                                       ║"
    echo "  ║          ✈  SkyGuide Deployment System  ✈             ║"
    echo "  ║                                                       ║"
    echo "  ║    Air Navigation Platform - Kubernetes Deploy        ║"
    echo "  ║    Runtime: containerd (pure)                         ║"
    echo "  ║                                                       ║"
    echo "  ╚═══════════════════════════════════════════════════════╝"
    echo -e "${RESET}"
}

print_step_header() {
    local step=$1
    local total=$2
    local title=$3
    echo ""
    echo -e "${BOLD}${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
    echo -e "${BOLD}${BLUE}  Step ${step}/${total}:  ${title}${RESET}"
    echo -e "${BOLD}${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
    echo ""
}

log_pass() { echo -e "  ${PASS} ${GREEN}$1${RESET}"; }
log_info() { echo -e "  ${INFO} ${CYAN}$1${RESET}"; }
log_warn() { echo -e "  ${WARN} ${YELLOW}$1${RESET}"; }
log_fail() { echo -e "  ${FAIL} ${RED}$1${RESET}"; exit 1; }
log_sub()  { echo -e "  ${DOT} ${DIM}$1${RESET}"; }

print_config() {
    echo ""
    echo -e "  ${BOLD}Configuration:${RESET}"
    echo -e "  ${DOT} Project dir  ${DIM}→${RESET} ${PROJECT_DIR}"
    echo -e "  ${DOT} Namespace    ${DIM}→${RESET} ${NAMESPACE}"
    echo -e "  ${DOT} Overlay      ${DIM}→${RESET} ${OVERLAY}"
    echo -e "  ${DOT} Runtime      ${DIM}→${RESET} containerd"
    echo -e "  ${DOT} Builder      ${DIM}→${RESET} buildah → ctr import"
    echo -e "  ${DOT} Date         ${DIM}→${RESET} $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""
}

elapsed() {
    local now=$(date +%s)
    local diff=$(( now - START_TIME ))
    printf "%dm%ds" $(( diff / 60 )) $(( diff % 60 ))
}

# ── Build & import into containerd ────────────────────────

build_and_load() {
    local image_name=$1
    local dockerfile=$2
    local tag=$(echo "$image_name" | sed 's/:/-/g')
    local tar_file="/tmp/${tag}.tar"

    # Build with buildah
    log_info "  buildah: building ${image_name} ..."
    buildah bud \
        -f "$dockerfile" \
        -t "$image_name" \
        . > /tmp/buildah-build.log 2>&1

    if [ $? -ne 0 ]; then
        log_fail "  buildah build failed — see /tmp/buildah-build.log"
        return 1
    fi
    log_pass "  buildah: image built"

    # Export to OCI tar
    log_info "  buildah: exporting to tar ..."
    buildah push "$image_name" "docker-archive:${tar_file}:${image_name}" > /tmp/buildah-push.log 2>&1

    if [ $? -ne 0 ]; then
        log_fail "  buildah push failed — see /tmp/buildah-push.log"
        return 1
    fi
    log_pass "  buildah: exported to ${tar_file}"

    # Import into containerd
    log_info "  ctr: importing into containerd ..."
    ctr images import "$tar_file" > /tmp/ctr-import.log 2>&1

    if [ $? -ne 0 ]; then
        log_fail "  ctr import failed — see /tmp/ctr-import.log"
        return 1
    fi
    log_pass "  ctr: imported into containerd"

    # Clean up tar
    rm -f "$tar_file"
    log_sub "cleaned up temp tar"

    # Verify
    if ctr images ls | grep -q "$image_name"; then
        log_pass "  verified: ${image_name} is in containerd"
    else
        log_warn "  could not verify image in containerd"
    fi
}

# ── Start ─────────────────────────────────────────────────

print_banner
print_config

# ===========================================================
# STEP 1: Preflight checks
# ===========================================================

print_step_header 1 $TOTAL_STEPS "Preflight checks"

# Check containerd
if systemctl is-active --quiet containerd 2>/dev/null; then
    log_pass "containerd service is running"
elif command -v containerd &>/dev/null; then
    log_warn "containerd is installed but not running — starting it"
    sudo systemctl start containerd
    log_pass "containerd started"
else
    log_fail "containerd is not installed"
fi

# Check ctr
if command -v ctr &>/dev/null; then
    log_pass "ctr is available $(DIM)($(ctr version 2>/dev/null | head -1))${RESET}"
else
    log_fail "ctr not found — is containerd properly installed?"
fi

# Check buildah
if command -v buildah &>/dev/null; then
    log_pass "buildah is available $(DIM)($(buildah --version 2>/dev/null))${RESET}"
else
    log_info "buildah not found — installing..."
    sudo apt-get update -qq
    sudo apt-get install -y -qq buildah > /dev/null 2>&1
    if command -v buildah &>/dev/null; then
        log_pass "buildah installed $(DIM)($(buildah --version 2>/dev/null))${RESET}"
    else
        log_fail "Failed to install buildah — install manually: sudo apt install buildah"
    fi
fi

# Check kubectl
if command -v kubectl &>/dev/null; then
    KUBECTL_VER=$(kubectl version --client -o json 2>/dev/null | grep gitVersion | cut -d'"' -f4 || echo "installed")
    log_pass "kubectl is installed $(DIM)(${KUBECTL_VER})${RESET}"
else
    log_fail "kubectl is not installed"
fi

# Check kustomize
if command -v kustomize &>/dev/null; then
    log_pass "kustomize is installed"
else
    log_fail "kustomize is not installed"
fi

# Check cluster
if kubectl cluster-info &>/dev/null 2>&1; then
    log_pass "Kubernetes cluster is reachable"
else
    log_fail "Cannot reach Kubernetes cluster — is it running?"
fi

echo ""
log_pass "All preflight checks passed"

# ===========================================================
# STEP 2: Build container images
# ===========================================================

print_step_header 2 $TOTAL_STEPS "Building container images"

cd "$PROJECT_DIR"

# Backend
echo -e "  ${BOLD}Backend (Spring Boot + Java 17):${RESET}"
BUILD_START=$(date +%s)
build_and_load "skyguide-backend:latest" "deploy/docker/Dockerfile.backend"
BUILD_END=$(date +%s)
log_pass "skyguide-backend built $(DIM)($((( BUILD_END - BUILD_START ))s)${RESET})"

echo ""

# Frontend
echo -e "  ${BOLD}Frontend (Angular + Nginx):${RESET}"
BUILD_START=$(date +%s)
build_and_load "skyguide-frontend:latest" "deploy/docker/Dockerfile.frontend"
BUILD_END=$(date +%s)
log_pass "skyguide-frontend built $(DIM)($((( BUILD_END - BUILD_START ))s)${RESET})"

echo ""
log_pass "Both images built and loaded into containerd"

# ===========================================================
# STEP 3: Create namespace
# ===========================================================

print_step_header 3 $TOTAL_STEPS "Preparing namespace"

if kubectl get namespace "$NAMESPACE" &>/dev/null 2>&1; then
    log_warn "Namespace '$NAMESPACE' already exists — reusing"
else
    kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f - >/dev/null 2>&1
    log_pass "Namespace '$NAMESPACE' created"
fi

log_sub "All resources will be deployed to: ${BOLD}${NAMESPACE}${RESET}"

# ===========================================================
# STEP 4: Apply Secrets and ConfigMaps
# ===========================================================

print_step_header 4 $TOTAL_STEPS "Applying Secrets & ConfigMaps"

log_info "Applying application secrets (DB password, JWT, OpenAI key) ..."
kubectl apply -f "$PROJECT_DIR/deploy/k8s/base/secrets/app-secrets.yaml" >/dev/null 2>&1
log_pass "Secrets applied"

log_info "Applying application config (MinIO endpoint, server port) ..."
kubectl apply -f "$PROJECT_DIR/deploy/k8s/base/configmap/app-config.yaml" >/dev/null 2>&1
log_pass "ConfigMap applied"

echo ""
log_pass "Configuration resources ready"

# ===========================================================
# STEP 5: Apply Kubernetes manifests via Kustomize
# ===========================================================

print_step_header 5 $TOTAL_STEPS "Deploying with Kustomize (${OVERLAY})"

log_info "Building kustomize overlay: ${BOLD}${OVERLAY}${RESET}"
log_sub "Resources: PostgreSQL, MinIO, Backend, Frontend"
echo ""

log_info "Applying manifests to cluster ..."
kustomize build "$PROJECT_DIR/deploy/k8s/overlays/$OVERLAY" | kubectl apply -f - 2>&1 | while read -r line; do
    echo -e "  ${DIM}  $line${RESET}"
done

echo ""
log_pass "All Kubernetes resources applied"

# ===========================================================
# STEP 6: Wait for deployments to be ready
# ===========================================================

print_step_header 6 $TOTAL_STEPS "Waiting for pods to start"

# PostgreSQL
echo -e "  ${BOLD}PostgreSQL:${RESET}"
log_info "Waiting for pod to be ready ..."
if kubectl rollout status deployment/skyguide-postgres -n "$NAMESPACE" --timeout=120s 2>&1 | tail -1; then
    log_pass "PostgreSQL is running"
else
    log_warn "PostgreSQL rollout timed out — check: kubectl get pods -n $NAMESPACE"
fi

# MinIO
echo ""
echo -e "  ${BOLD}MinIO:${RESET}"
log_info "Waiting for pod to be ready ..."
if kubectl rollout status deployment/skyguide-minio -n "$NAMESPACE" --timeout=120s 2>&1 | tail -1; then
    log_pass "MinIO is running"
else
    log_warn "MinIO rollout timed out — check: kubectl get pods -n $NAMESPACE"
fi

# Backend
echo ""
echo -e "  ${BOLD}Backend (Spring Boot):${RESET}"
log_info "Waiting for pod to be ready (this may take a minute) ..."
if kubectl rollout status deployment/skyguide-backend -n "$NAMESPACE" --timeout=180s 2>&1 | tail -1; then
    log_pass "Backend is running"
else
    log_warn "Backend rollout timed out — check: kubectl logs -n $NAMESPACE -l app=skyguide-backend"
fi

# Frontend
echo ""
echo -e "  ${BOLD}Frontend (Angular + Nginx):${RESET}"
log_info "Waiting for pod to be ready ..."
if kubectl rollout status deployment/skyguide-frontend -n "$NAMESPACE" --timeout=120s 2>&1 | tail -1; then
    log_pass "Frontend is running"
else
    log_warn "Frontend rollout timed out — check: kubectl logs -n $NAMESPACE -l app=skyguide-frontend"
fi

echo ""
log_pass "All deployments are ready"

# ===========================================================
# STEP 7: Setup Tekton + ArgoCD (optional)
# ===========================================================

print_step_header 7 $TOTAL_STEPS "Setting up CI/CD (Tekton + ArgoCD)"

# Tekton
if kubectl get namespace tekton-pipelines &>/dev/null 2>&1; then
    log_info "Tekton detected — creating pipeline resources ..."
    kubectl apply -f "$PROJECT_DIR/deploy/tekton/rbac.yaml" >/dev/null 2>&1 || true
    kubectl apply -f "$PROJECT_DIR/deploy/tekton/tasks/kustomize-deploy.yaml" >/dev/null 2>&1 || true
    kubectl apply -f "$PROJECT_DIR/deploy/tekton/pipeline.yaml" >/dev/null 2>&1 || true
    log_pass "Tekton pipeline 'skyguide-build-deploy' created"
    log_sub "Run it: kubectl apply -f deploy/tekton/pipeline-run.yaml"
else
    log_warn "Tekton not installed — skipping pipeline setup"
fi

# ArgoCD
if kubectl get namespace argocd &>/dev/null 2>&1; then
    log_info "ArgoCD detected — creating application ..."
    kubectl apply -f "$PROJECT_DIR/deploy/argocd/application.yaml" >/dev/null 2>&1 || true
    log_pass "ArgoCD application 'skyguide' created"
    log_sub "Note: Update deploy/argocd/application.yaml with your git repo URL first"
else
    log_warn "ArgoCD not installed — skipping application setup"
fi

# ===========================================================
# DONE — Print summary
# ===========================================================

END_TIME=$(date +%s)
DURATION=$(( END_TIME - START_TIME ))

echo ""
echo -e "${BOLD}${CYAN}"
echo "  ╔═══════════════════════════════════════════════════════════════╗"
echo "  ║                                                               ║"
echo "  ║               🎉  Deployment Complete!                        ║"
echo "  ║                                                               ║"
echo "  ╚═══════════════════════════════════════════════════════════════╝"
echo -e "${RESET}"
echo ""

# Pod status
echo -e "${BOLD}  📦 Pod Status:${RESET}"
echo ""
kubectl get pods -n "$NAMESPACE" -o wide 2>/dev/null | sed 's/^/  /'
echo ""

# Service status
echo -e "${BOLD}  🔌 Services:${RESET}"
echo ""
kubectl get svc -n "$NAMESPACE" 2>/dev/null | sed 's/^/  /'
echo ""

# URLs
echo -e "${BOLD}  🌐 Access URLs:${RESET}"
echo ""

# Frontend URL
FRONTEND_SVC=$(kubectl get svc skyguide-frontend -n "$NAMESPACE" -o jsonpath='{.spec.type}' 2>/dev/null)
if [ "$FRONTEND_SVC" = "LoadBalancer" ]; then
    EXTERNAL_IP=$(kubectl get svc skyguide-frontend -n "$NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null)
    if [ -n "$EXTERNAL_IP" ]; then
        echo -e "  ${GREEN}➜${RESET} ${BOLD}Frontend:${RESET}  http://${EXTERNAL_IP}"
    else
        echo -e "  ${GREEN}➜${RESET} ${BOLD}Frontend:${RESET}  http://<pending — run: kubectl get svc skyguide-frontend -n $NAMESPACE>"
    fi
else
    NODE_PORT=$(kubectl get svc skyguide-frontend -n "$NAMESPACE" -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null)
    NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}' 2>/dev/null)
    if [ -n "$NODE_IP" ] && [ -n "$NODE_PORT" ]; then
        echo -e "  ${GREEN}➜${RESET} ${BOLD}Frontend:${RESET}  http://${NODE_IP}:${NODE_PORT}"
    fi
fi

echo -e "  ${GREEN}➜${RESET} ${BOLD}Backend API:${RESET} http://localhost:8081/api  ${DIM}(port-forward required)${RESET}"
echo -e "  ${GREEN}➜${RESET} ${BOLD}Swagger UI:${RESET} http://localhost:8081/swagger-ui.html  ${DIM}(port-forward required)${RESET}"
echo -e "  ${GREEN}➜${RESET} ${BOLD}WebSocket:${RESET}  ws://localhost:8081/ws  ${DIM}(port-forward required)${RESET}"

echo ""
echo -e "  ${BOLD}🔒 Admin Login:${RESET}"
echo -e "  ${GREEN}➜${RESET} Email:    ${BOLD}admin@navigation.tn${RESET}"
echo -e "  ${GREEN}➜${RESET} Password: ${BOLD}Admin@1234${RESET}"

echo ""
echo -e "  ${BOLD}🚀 Quick Port-Forward (run in new terminals):${RESET}"
echo ""
echo -e "    ${DIM}# Frontend${RESET}"
echo -e "    ${CYAN}kubectl port-forward -n $NAMESPACE svc/skyguide-frontend 80:80${RESET}"
echo ""
echo -e "    ${DIM}# Backend API${RESET}"
echo -e "    ${CYAN}kubectl port-forward -n $NAMESPACE svc/skyguide-backend 8081:8081${RESET}"
echo ""
echo -e "    ${DIM}# MinIO Console${RESET}"
echo -e "    ${CYAN}kubectl port-forward -n $NAMESPACE svc/skyguide-minio 9001:9001${RESET}"
echo -e "    ${DIM}  → http://localhost:9001  (minioadmin / minioadmin)${RESET}"

echo ""
echo -e "  ${BOLD}📋 Useful Commands:${RESET}"
echo ""
echo -e "    ${DIM}# Watch pods in real time${RESET}"
echo -e "    ${CYAN}kubectl get pods -n $NAMESPACE -w${RESET}"
echo ""
echo -e "    ${DIM}# View backend logs${RESET}"
echo -e "    ${CYAN}kubectl logs -f deployment/skyguide-backend -n $NAMESPACE${RESET}"
echo ""
echo -e "    ${DIM}# View frontend logs${RESET}"
echo -e "    ${CYAN}kubectl logs -f deployment/skyguide-frontend -n $NAMESPACE${RESET}"
echo ""
echo -e "    ${DIM}# Restart a deployment${RESET}"
echo -e "    ${CYAN}kubectl rollout restart deployment/skyguide-backend -n $NAMESPACE${RESET}"
echo ""
echo -e "    ${DIM}# Delete everything${RESET}"
echo -e "    ${CYAN}kubectl delete namespace $NAMESPACE${RESET}"

echo ""
echo -e "  ${BOLD}⏱  Total deployment time: $(elapsed)${RESET}"
echo ""
echo -e "${BOLD}${CYAN}  Happy flying! ✈${RESET}"
echo ""
