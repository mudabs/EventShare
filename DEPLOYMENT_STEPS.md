# EventShare vps01 Deployment Steps

This is the short checklist for deploying EventShare to the tunneled home server.

## What runs

The current production deployment starts the core services:

- `postgres`
- `api`
- `frontend`
- `nginx`

Prometheus and Grafana are available through the opt-in `monitoring` profile. RabbitMQ and the
standalone worker are deferred for a later stage; media processing currently runs inside `api`.

## Prerequisites

- SSH access to `vps01` through the local alias `myvps`
- Docker Engine and the Compose plugin installed on `vps01`
- A cloned copy of this repo at `~/apps/eventshare`
- A filled-out `.env` file at the repo root

## First-time deployment

1. Verify SSH access:

```powershell
ssh myvps
```

2. On `vps01`, clone and configure the repo:

```bash
mkdir -p ~/apps
cd ~/apps
git clone <your-repo-url> eventshare
cd ~/apps/eventshare
cp .env.example .env
```

3. Edit `.env` and set at least:

- `APP_BASE_URL`
- `NEXT_PUBLIC_APP_BASE_URL`
- `NEXT_PUBLIC_API_BASE_URL`
- `CORS_ALLOWED_ORIGINS`
- `POSTGRES_*`
- `R2_*`
- `CLERK_*`
- `GRAFANA_ADMIN_USER`
- `GRAFANA_ADMIN_PASSWORD`

4. Start the deployment:

```bash
bash scripts/deploy-prod.sh
```

5. Check service status and the API:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.override.yml ps
curl -fsSL http://127.0.0.1:8088/api/ping -L
```

6. If needed, check logs:

```bash
cd ~/apps/eventshare
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.override.yml logs -f api
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.override.yml logs -f frontend
```

## Normal updates

From Windows:

```powershell
.\scripts\deploy-to-myvps.ps1
```

Or on `vps01`:

```bash
cd ~/apps/eventshare
git pull --ff-only
bash scripts/deploy-prod.sh
```

## Troubleshooting

- If `curl http://127.0.0.1:8088/api/ping` fails, check the app Nginx container and the host
  Nginx/Tailscale path.
- If Compose says no configuration file was provided, make sure you are in `~/apps/eventshare`.
- If an SSH session disconnects during a build, reconnect and run `bash scripts/deploy-prod.sh`;
  containers continue running independently of the SSH session.

## Rollback

1. Check out the previous commit or tag.
2. Run `bash scripts/deploy-prod.sh`.

## Notes

- The frontend uses `NEXT_PUBLIC_API_BASE_URL` baked into its image at build time.
- If IONOS remains the public gateway, its Nginx upstream must use the vps01 Tailscale address,
  not `192.168.0.104`.
- RabbitMQ and the standalone worker are intentionally deferred and should be reintroduced in a
  separate migration when the broker-backed processing path is needed.
