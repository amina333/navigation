#!/bin/bash
set -e

# ============================================================
# SkyGuide - Tekton Setup Script
# Installs Tekton Pipelines + creates the pipeline
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "============================================"
echo "  SkyGuide Tekton Pipeline Setup"
echo "============================================"

# 1. Install Tekton Pipelines
echo "[1/4] Installing Tekton Pipelines..."
kubectl apply -f https://storage.googleapis.com/tekton-releases/pipeline/latest/release.yaml

echo "Waiting for Tekton to be ready..."
kubectl wait --for=condition=ready pod -l app=tekton-pipelines-controller -n tekton-pipelines --timeout=120s

# 2. Install Tekton Dashboard (optional)
echo "[2/4] Installing Tekton Dashboard..."
kubectl apply -f https://storage.googleapis.com/tekton-releases/dashboard/latest/release.yaml 2>/dev/null || true

# 3. Create namespace + RBAC
echo "[3/4] Creating namespace and RBAC..."
kubectl apply -f "$PROJECT_DIR/tekton/rbac.yaml"

# 4. Create pipeline + tasks
echo "[4/4] Creating Tekton pipeline..."
kubectl apply -f "$PROJECT_DIR/tekton/tasks/kustomize-deploy.yaml"
kubectl apply -f "$PROJECT_DIR/tekton/pipeline.yaml"

echo ""
echo "============================================"
echo "  Tekton pipeline ready!"
echo "============================================"
echo ""
echo "  To run the pipeline:"
echo "    kubectl apply -f deploy/tekton/pipeline-run.yaml"
echo ""
echo "  To watch pipeline runs:"
echo "    kubectl get pipelineruns -n skyguide -w"
echo ""
echo "  Tekton Dashboard (if installed):"
echo "    kubectl port-forward -n tekton-pipelines svc/tekton-dashboard 9097:9097"
echo "    Then open: http://localhost:9097"
echo ""
