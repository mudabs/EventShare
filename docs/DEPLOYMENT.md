# Deployment Guide

Target: a single VPS with roughly 4 vCPU and 8 GB RAM running Docker and Docker Compose.
In production, host Nginx terminates TLS and forwards to the container Nginx listening on
`127.0.0.1:8088`.

## 1. Provision the host

Install Docker Engine and the Compose plugin. Create a non-root user in the docker group.
Open ports 80 and 443. Point your domain's DNS A record at the VPS.

## 2. External services

Create a Cloudflare R2 bucket and an R2 API token (account id, access key, secret). Create a
Clerk application and enable Email and Google sign-in; note the publishable key, secret key,
and the frontend API (issuer) URL.

## 3. Clone and configure

```bash
git clone <your-repo-url> eventshare && cd eventshare
cp .env.example .env
# Edit .env: set APP_BASE_URL and NEXT_PUBLIC_* to your domain, fill R2_* and CLERK_*,
# and set strong POSTGRES_PASSWORD, RABBITMQ_DEFAULT_PASS, GRAFANA_ADMIN_PASSWORD.
```

The compose file reads `.env` automatically. The frontend image bakes NEXT_PUBLIC_* values
at build time from the compose build args, which are sourced from `.env`.

## 4. First run

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f api
```

The API runs Flyway migrations on startup, creating the schema. Visit `http://your-domain`.
The API is at `/api`, Grafana at `/grafana` (log in with the admin credentials, the
Prometheus datasource and the EventShare dashboard are provisioned automatically).

## 5. TLS

Use your existing host Nginx plus Certbot. Add a new site file based on
`deploy/nginx/eventshare.conf` and point it at `http://127.0.0.1:8088`. Then let Certbot
manage the HTTPS block:

```bash
sudo cp deploy/nginx/eventshare.conf /etc/nginx/sites-available/eventshare.conf
sudo ln -sfn /etc/nginx/sites-available/eventshare.conf /etc/nginx/sites-enabled/eventshare.conf
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d eventshare.munashemudabura.com
```

Update APP_BASE_URL, NEXT_PUBLIC_APP_BASE_URL, NEXT_PUBLIC_API_BASE_URL, and
CORS_ALLOWED_ORIGINS to the https URLs and rebuild the stack. For production deploys, use
`docker-compose.prod.yml` so the container Nginx only binds to localhost:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build --remove-orphans
```

## 6. CI/CD

`.github/workflows/ci.yml` builds and tests on every push and pull request.
`.github/workflows/deploy.yml` deploys automatically on push to `main` and also supports
manual dispatch. Add repository secrets VPS_HOST, VPS_USER, VPS_SSH_KEY, and VPS_APP_DIR.
The deploy step runs `scripts/deploy-prod.sh` on the VPS.

## 7. Updating and rollback

Update: `git pull && docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build --remove-orphans`.
Compose recreates only changed services.
Rollback: check out the previous commit or tag and run the same command. Because Flyway
migrations are forward-only, write expand-and-contract migrations so an older image still runs
against a newer schema during a rollback window.

## 8. Verify a healthy deployment

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
curl -fsSL http://127.0.0.1:8088/api/ping -L
docker compose exec rabbitmq rabbitmq-diagnostics -q ping
```
