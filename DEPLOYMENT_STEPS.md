# EventShare VPS Deployment Steps

This file is the short, practical checklist for deploying EventShare to the VPS.

## What runs

The current `docker-compose.yml` starts 8 services:

- `postgres`
- `rabbitmq`
- `api`
- `worker`
- `frontend`
- `nginx`
- `prometheus`
- `grafana`

In production, host Nginx terminates TLS and forwards to the app's container Nginx on
`127.0.0.1:8088`. The rest stay on private Docker networks.

## Prerequisites

- SSH access to the VPS as `munashe`
- Docker Engine installed on the VPS
- Docker Compose plugin installed on the VPS
- A cloned copy of this repo on the VPS
- A filled-out `.env` file at the repo root

## First-time deployment

1. SSH into the server:

```bash
ssh munashe@66.179.81.222
```

2. Go to the repo root on the VPS:

```bash
mkdir -p ~/apps
cd ~/apps
git clone <your-repo-url> eventshare
cd ~/apps/eventshare
```

3. Pull the latest code:

```bash
git pull
```

4. Create or update the environment file:

```bash
cp .env.example .env
```

5. Edit `.env` and set at least these values:

- `APP_BASE_URL`
- `NEXT_PUBLIC_APP_BASE_URL`
- `NEXT_PUBLIC_API_BASE_URL`
- `CORS_ALLOWED_ORIGINS`
- `POSTGRES_*`
- `RABBITMQ_*`
- `R2_*`
- `CLERK_*`
- `GRAFANA_ADMIN_USER`
- `GRAFANA_ADMIN_PASSWORD`

6. Start everything:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build --remove-orphans
```

7. Check service status:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
```

8. Check the API health endpoint:

```bash
curl -fsSL http://127.0.0.1:8088/api/ping -L
```

9. If needed, check logs:

```bash
cd ~/apps/eventshare
docker compose logs -f api
docker compose logs -f worker
docker compose logs -f frontend
```

## Normal updates

When you want to deploy a new version:

```bash
ssh munashe@66.179.81.222
cd ~/apps/eventshare
git pull
bash scripts/deploy-prod.sh
```

## Troubleshooting

- If `curl http://127.0.0.1:8088/api/ping` fails, check the app Nginx container and the host Nginx site config.
- If `docker compose logs -f ...` says `no configuration file provided`, make sure you are in `~/apps/eventshare` first.
- If the SSH session disconnects during a long build, reconnect and run:

```bash
cd ~/apps/eventshare
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
docker compose logs -f api
```

The containers keep running on the VPS even if your SSH session drops.

## Rollback

If a release causes problems:

1. Check out the previous commit or tag.
2. Run:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build --remove-orphans
```

## Notes

- The frontend talks to the backend API through the URL set in `NEXT_PUBLIC_API_BASE_URL`.
- The mobile app does not need the frontend to be up, but it does need the backend API.
- If you move to HTTPS later, update the `APP_BASE_URL`, `NEXT_PUBLIC_APP_BASE_URL`, `NEXT_PUBLIC_API_BASE_URL`, and `CORS_ALLOWED_ORIGINS` values, then rebuild the frontend.
