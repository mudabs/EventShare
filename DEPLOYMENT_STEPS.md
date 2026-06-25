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

Only `nginx` is exposed to the internet. The rest stay on private Docker networks.

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
docker compose up -d --build
```

7. Check service status:

```bash
docker compose ps
```

8. Check the API health endpoint:

```bash
curl -fsS http://localhost/api/ping
```

9. If needed, check logs:

```bash
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
docker compose up -d --build
```

## Rollback

If a release causes problems:

1. Check out the previous commit or tag.
2. Run:

```bash
docker compose up -d --build
```

## Notes

- The frontend talks to the backend API through the URL set in `NEXT_PUBLIC_API_BASE_URL`.
- The mobile app does not need the frontend to be up, but it does need the backend API.
- If you move to HTTPS later, update the `APP_BASE_URL`, `NEXT_PUBLIC_APP_BASE_URL`, `NEXT_PUBLIC_API_BASE_URL`, and `CORS_ALLOWED_ORIGINS` values, then rebuild the frontend.
