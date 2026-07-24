#!/bin/bash
set -e

# ============================================================
# SkyGuide - One-Time VM Setup Script
# Run this ONCE on your Ubuntu VM to prepare everything
# ============================================================

echo "============================================"
echo "  SkyGuide VM Setup"
echo "============================================"

# 1. Install Docker (if not installed)
if ! command -v docker &> /dev/null; then
    echo "[1/6] Installing Docker..."
    sudo apt-get update
    sudo apt-get install -y docker.io
    sudo systemctl enable docker
    sudo systemctl start docker
    sudo usermod -aG docker $USER
else
    echo "[1/6] Docker already installed"
fi

# 2. Install kubectl (if not installed)
if ! command -v kubectl &> /dev/null; then
    echo "[2/6] Installing kubectl..."
    curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
    chmod +x kubectl
    sudo mv kubectl /usr/local/bin/
else
    echo "[2/6] kubectl already installed"
fi

# 3. Install kustomize (if not installed)
if ! command -v kustomize &> /dev/null; then
    echo "[3/6] Installing kustomize..."
    curl -s "https://raw.githubusercontent.com/kubernetes-sigs/kustomize/master/hack/install_kustomize.sh" | bash
    sudo mv kustomize /usr/local/bin/
else
    echo "[3/6] kustomize already installed"
fi

# 4. Enable Minikube addons (if using Minikube)
if command -v minikube &> /dev/null; then
    echo "[4/6] Enabling Minikube registry addon..."
    minikube addons enable registry
    eval $(minikube docker-env)
else
    echo "[4/6] Minikube not detected, skipping (ensure your k8s cluster is running)"
fi

# 5. Build Docker images inside the k8s cluster
echo "[5/6] Building Docker images..."
cd "$(dirname "$0")/.."
docker build -f deploy/docker/Dockerfile.backend -t skyguide-backend:latest .
docker build -f deploy/docker/Dockerfile.frontend -t skyguide-frontend:latest .

# 6. Apply Kubernetes manifests
echo "[6/6] Applying Kubernetes manifests (dev overlay)..."
kubectl apply -f deploy/k8s/base/secrets/
kubectl apply -f deploy/k8s/base/configmap/
kustomize build deploy/k8s/overlays/dev | kubectl apply -f -

echo ""
echo "============================================"
echo "  SkyGuide deployed successfully!"
echo "============================================"
echo ""
echo "  Frontend:  http://<VM_IP>:<NodePort>"
echo "  Backend:   http://<VM_IP>:8081/api"
echo ""
echo "  To check pods:  kubectl get pods -n skyguide"
echo "  To check logs:  kubectl logs -f deployment/skyguide-backend -n skyguide"
echo ""
echo "  Admin login: admin@navigation.tn / Admin@1234"
echo ""
