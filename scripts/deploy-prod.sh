#!/usr/bin/env bash
# Production deploy for the EventShare VPS.
#
# Run this from the checked-out repo on the VPS:
#   cd ~/apps/eventshare && bash scripts/deploy-prod.sh
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "[deploy] pulling latest code"
git pull --ff-only

echo "[deploy] building and starting services"
docker compose -f docker-compose.yml -f docker-compose.prod.yml build
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --remove-orphans

echo "[deploy] waiting for services"
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps

echo "[deploy] checking local edge endpoint"
timeout 30s curl -fsSL http://127.0.0.1:8088/api/ping -L

echo "[deploy] pruning unused images"
docker image prune -f

echo "[deploy] done"
