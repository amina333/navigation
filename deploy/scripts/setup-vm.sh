#!/bin/bash
set -e
# ============================================================
# SkyGuide - One-Time VM Setup Script
# Cluster runtime is containerd (kubeadm): images must be imported
# into containerd's k8s.io namespace, not just built with Docker.
# ============================================================

cd "$(dirname "$0")/../.."   # repo root (was: deploy/, which broke the -f paths)

echo "[1/4] Installing tools..."
command -v buildah  >/dev/null || { sudo apt-get update && sudo apt-get install -y buildah; }
if ! command -v kubectl >/dev/null; then
  curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
  chmod +x kubectl && sudo mv kubectl /usr/local/bin/
fi

echo "[2/4] Building images and importing into containerd..."
for app in backend frontend; do
  ref="docker.io/library/skyguide-$app:latest"
  sudo buildah bud -f "deploy/docker/Dockerfile.$app" -t "$ref" .
  sudo rm -f /tmp/skyguide-$app.tar
  sudo buildah push "$ref" "docker-archive:/tmp/skyguide-$app.tar:$ref"
  sudo ctr -n k8s.io images import /tmp/skyguide-$app.tar
done

echo "[3/4] Creating namespace + secret (from deploy/secrets.env)..."
kubectl create ns skyguide --dry-run=client -o yaml | kubectl apply -f -
kubectl -n skyguide create secret generic app-secrets --from-env-file=deploy/secrets.env \
  --dry-run=client -o yaml | kubectl apply -f -

echo "[4/4] Applying dev overlay..."
kubectl apply -k deploy/k8s/overlays/dev

echo "Done. Check pods: kubectl get pods -n skyguide"
