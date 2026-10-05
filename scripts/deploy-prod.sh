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
EDGE_URL="${EVENTSHARE_EDGE_URL:-http://100.110.81.2:8088}"

echo "[deploy] pulling latest code"
git pull --ff-only

echo "[deploy] building and starting services"
docker compose "${COMPOSE_ARGS[@]}" build
docker compose "${COMPOSE_ARGS[@]}" up -d --remove-orphans

# The API container can receive a new Docker-network IP when it is rebuilt.
# Restart the edge proxy so its upstream resolution cannot remain stale.
echo "[deploy] refreshing edge proxy"
docker compose "${COMPOSE_ARGS[@]}" restart nginx

echo "[deploy] waiting for services"
docker compose "${COMPOSE_ARGS[@]}" ps

echo "[deploy] checking local edge endpoint"
for attempt in 1 2 3 4 5 6; do
if curl -fsSL "${EDGE_URL}/api/ping" -L; then
    break
  fi
  echo "[deploy] edge not ready yet, retrying ($attempt/6)"
  sleep 10
done
curl -fsSL "${EDGE_URL}/api/ping" -L

echo "[deploy] pruning unused images"
docker image prune -f

echo "[deploy] done"
