#!/bin/bash
set -e

# ============================================================
# SkyGuide - ArgoCD Setup Script
# Installs ArgoCD and creates the Application
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "============================================"
echo "  SkyGuide ArgoCD Setup"
echo "============================================"

# 1. Install ArgoCD
echo "[1/3] Installing ArgoCD..."
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

echo "Waiting for ArgoCD to be ready..."
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=argocd-server -n argocd --timeout=180s

# 2. Get initial admin password
echo "[2/3] ArgoCD initial admin password:"
echo "  Username: admin"
echo -n "  Password: "
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
echo ""

# 3. Apply ArgoCD Application
echo "[3/3] Creating ArgoCD Application..."
kubectl apply -f "$PROJECT_DIR/argocd/application.yaml"

echo ""
echo "============================================"
echo "  ArgoCD setup complete!"
echo "============================================"
echo ""
echo "  Access ArgoCD UI:"
echo "    kubectl port-forward -n argocd svc/argocd-server 8080:443"
echo "    Then open: https://localhost:8080"
echo ""
echo "  NOTE: Update deploy/argocd/application.yaml"
echo "        with your git repo URL before syncing!"
echo ""
