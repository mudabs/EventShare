#!/usr/bin/env bash
# One-command local interview demo (docs/DEMO.md).
#   First run:  creates .env.demo with generated passwords, then asks for Clerk/R2 test keys.
#   Next runs:  starts the demo stack on http://localhost:8090 and prints the demo logins.
# Options:  --down   stop the demo stack (data kept)
#           --wipe   stop it and delete its database volume (fresh seed on next start)
set -euo pipefail
cd "$(dirname "$0")/.."

ENV_FILE=.env.demo
PROJECT=eventshare-demo
COMPOSE=(docker compose -p "$PROJECT" --env-file "$ENV_FILE")

case "${1:-}" in
  --down) "${COMPOSE[@]}" down; exit 0 ;;
  --wipe) "${COMPOSE[@]}" down -v; exit 0 ;;
esac

gen() { head -c 512 /dev/urandom | LC_ALL=C tr -dc 'A-Za-z0-9' | cut -c1-"${1:-24}"; }

if [[ ! -f "$ENV_FILE" ]]; then
  db="$(gen 32)"
  tmp="$(mktemp)"
  # The database password must match on both lines; every other GENERATED value is unique.
  while IFS= read -r line || [[ -n "$line" ]]; do
    case "$line" in
      POSTGRES_PASSWORD=GENERATED|SPRING_DATASOURCE_PASSWORD=GENERATED) echo "${line%%=*}=$db" ;;
      *=GENERATED) echo "${line%%=*}=Demo-$(gen 16)" ;;
      *) echo "$line" ;;
    esac
  done < .env.demo.example > "$tmp"
  chmod 600 "$tmp"
  mv "$tmp" "$ENV_FILE"
  echo "Created $ENV_FILE with generated passwords."
fi

missing="$(grep -E '^[A-Z0-9_]+=.*PASTE' "$ENV_FILE" | cut -d= -f1 || true)"
if [[ -n "$missing" ]]; then
  echo "Fill these in $ENV_FILE (Clerk development instance + demo R2 bucket), then run again:"
  echo "$missing" | sed 's/^/  - /'
  exit 1
fi

"${COMPOSE[@]}" up -d --build

val() { grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2-; }
port="$(val EVENTSHARE_HTTP_PORT)"
cat <<INFO

EventShare demo is starting on http://localhost:${port:-8090}
(first start builds images and seeds about 20 photos; allow 1 to 3 minutes)

  Guest link   http://localhost:${port:-8090}/e/$(val DEMO_INVITE_CODE)
  Host login   username $(val DEMO_HOST_USERNAME)   password $(val DEMO_HOST_PASSWORD)
  Admin login  username $(val DEMO_ADMIN_USERNAME)  password $(val DEMO_ADMIN_PASSWORD)
  Promo code   $(val DEMO_PROMO_CODE)

  Logs:  docker compose -p $PROJECT logs -f api
  Stop:  scripts/demo-up.sh --down     Fresh start:  scripts/demo-up.sh --wipe
INFO
