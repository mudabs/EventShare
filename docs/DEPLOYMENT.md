# Deployment Guide

Target: the tunneled home server `vps01`, reached interactively with the local SSH alias
`myvps`. Docker Compose runs the application on that host. The IONOS VPS is no longer the
application deployment target; it may remain a public reverse-proxy gateway over Tailscale.

The current deployment intentionally runs the API's in-process media scheduler. RabbitMQ and
the standalone worker remain in the repository as a deferred architecture stage and are not
started by the current Compose files.

## 1. Provision the host

Install Docker Engine and the Compose plugin on `vps01`. Create a non-root user in the docker
group. Verify the local SSH alias before deploying:

```powershell
ssh myvps
```

For private access, keep the application reachable through the tunnel. If IONOS remains the
public gateway, point its Nginx upstream at the vps01 Tailscale address rather than the home-LAN
address.

## 2. External services

Create a Cloudflare R2 bucket and an R2 API token (account id, access key, secret). Create a
Clerk application and enable Email and Google sign-in; note the publishable key, secret key,
and the frontend API (issuer) URL.

## 3. Clone and configure

On `vps01`:

```bash
mkdir -p ~/apps
cd ~/apps
git clone <your-repo-url> eventshare
cd eventshare
cp .env.example .env
# Edit .env: set APP_BASE_URL and NEXT_PUBLIC_* to the URL users will access, fill R2_* and
# CLERK_*, and set strong POSTGRES_PASSWORD and GRAFANA_ADMIN_PASSWORD.
```

The compose file reads `.env` automatically. The frontend image bakes NEXT_PUBLIC_* values
at build time from the compose build args, which are sourced from `.env`.

## 4. First run

```bash
bash scripts/deploy-prod.sh
```

The API runs Flyway migrations on startup, creating the schema. The default service set is
`postgres`, `api`, `frontend`, and `nginx`. Prometheus and Grafana are opt-in through the
`monitoring` profile.

## 5. TLS and public routing

The container Nginx listens on the vps01 Tailscale address `100.110.81.2:8088`. If IONOS remains
the public gateway, install the host Nginx site there and point it over Tailscale at that vps01 address;
do not point it at `192.168.0.104`, which only exists on the home LAN.

The existing site template is `deploy/nginx/eventshare.conf`. Update APP_BASE_URL,
NEXT_PUBLIC_APP_BASE_URL, NEXT_PUBLIC_API_BASE_URL, and CORS_ALLOWED_ORIGINS to the HTTPS URL,
then redeploy so the frontend is rebuilt with the correct public API URL.

## 6. CI/CD

`.github/workflows/deploy.yml` runs directly on `vps01` through a self-hosted GitHub Actions
runner with the labels `self-hosted`, `linux`, and `eventshare-vps01`. It deploys automatically
after CI passes on `main` and also supports manual dispatch. Remove or relabel the old runner
on IONOS so it cannot receive this job.

The deploy job runs `scripts/deploy-prod.sh` locally on the server from:

- default: `$HOME/apps/eventshare`
- optional override: runner environment variable `VPS_APP_DIR`

### Self-hosted runner setup (one-time on vps01)

1. In GitHub: Settings -> Actions -> Runners -> New self-hosted runner (Linux x64).
2. On vps01, run the generated download/configure commands.
3. During configure, add the label `eventshare-vps01`.
4. Install and start the runner service:

```bash
./svc.sh install
./svc.sh start
```

5. Verify the runner is online in GitHub before pushing to `main`.

## 7. Updating and rollback

Update: `git pull --ff-only && bash scripts/deploy-prod.sh`.
Compose recreates only changed services.

Rollback: check out the previous commit or tag and run the same command. Because Flyway
migrations are forward-only, write expand-and-contract migrations so an older image still runs
against a newer schema during a rollback window.

## 8. Verify a healthy deployment

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.override.yml ps
curl -fsSL http://100.110.81.2:8088/api/ping -L
```

RabbitMQ and the standalone worker are deliberately not part of this deployment yet. The
broker-backed implementation can be reintroduced as a later, separate migration.

## 9. Manual deployment from Windows

After the repository has been cloned to `~/apps/eventshare` on `vps01`, run from the repository
root:

```powershell
.\scripts\deploy-to-myvps.ps1
```

This uses the local `myvps` SSH configuration and runs the same production deploy script on the
tunneled host.
