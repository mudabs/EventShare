#!/usr/bin/env bash
# Production deploy for EventShare on the tunneled vps01 host.
#
# Run this from the checked-out repo on the VPS:
#   cd ~/apps/eventshare && bash scripts/deploy-prod.sh
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# The override is intentional for vps01: it caps container memory and keeps
# monitoring opt-in. RabbitMQ and the standalone worker are deferred and are
# not part of the current production Compose topology.
COMPOSE_ARGS=(-f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.override.yml)

echo "[deploy] pulling latest code"
git pull --ff-only

echo "[deploy] building and starting services"
docker compose "${COMPOSE_ARGS[@]}" build
docker compose "${COMPOSE_ARGS[@]}" up -d --remove-orphans

echo "[deploy] waiting for services"
docker compose "${COMPOSE_ARGS[@]}" ps

echo "[deploy] checking local edge endpoint"
for attempt in 1 2 3 4 5 6; do
  if curl -fsSL http://127.0.0.1:8088/api/ping -L; then
    break
  fi
  echo "[deploy] edge not ready yet, retrying ($attempt/6)"
  sleep 10
done
curl -fsSL http://127.0.0.1:8088/api/ping -L

echo "[deploy] pruning unused images"
docker image prune -f

echo "[deploy] done"
