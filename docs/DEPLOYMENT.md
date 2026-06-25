# Deployment Guide

Target: a single VPS with roughly 4 vCPU and 8 GB RAM running Docker and Docker Compose.
Everything runs as containers defined in `docker-compose.yml`; only nginx is published to
the host.

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

Issue certificates with certbot (host nginx or the standalone mode), or place
`fullchain.pem` and `privkey.pem` in `infra/nginx/certs`. Then enable the HTTPS server block
in `infra/nginx/conf.d/default.conf` (it is provided, commented) and reload nginx:

```bash
docker compose exec nginx nginx -s reload
```

Update APP_BASE_URL, NEXT_PUBLIC_APP_BASE_URL, NEXT_PUBLIC_API_BASE_URL, and
CORS_ALLOWED_ORIGINS to the https URLs and rebuild the frontend
(`docker compose up -d --build frontend`).

## 6. CI/CD

`.github/workflows/ci.yml` builds and tests on every push and pull request.
`.github/workflows/deploy.yml` deploys over SSH on manual dispatch. Add repository secrets
VPS_HOST, VPS_USER, VPS_SSH_KEY, and VPS_APP_DIR. The deploy step runs
`git pull && docker compose build && docker compose up -d` on the VPS.

## 7. Updating and rollback

Update: `git pull && docker compose up -d --build`. Compose recreates only changed services.
Rollback: check out the previous commit or tag and run the same command. Because Flyway
migrations are forward-only, write expand-and-contract migrations so an older image still runs
against a newer schema during a rollback window.

## 8. Verify a healthy deployment

```bash
docker compose ps                 # all services Up / healthy
curl -fsS http://localhost/api/ping
docker compose exec rabbitmq rabbitmq-diagnostics -q ping
```
