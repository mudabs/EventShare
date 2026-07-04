#!/usr/bin/env bash
# Production deploy for the EventShare VPS.
#
# Run this from the checked-out repo on the VPS:
#   cd ~/apps/eventshare && bash scripts/deploy-prod.sh
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

git pull --ff-only
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build --remove-orphans
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
curl -fsSL http://127.0.0.1:8088/api/ping -L
docker image prune -f
