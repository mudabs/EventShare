# EventShare Production Deployment Runbook

This document records the reproducible deployment used for EventShare on the tunneled vps01
server. It is both an operator checklist and an explanation of why each step exists.

The attached home-server build guide is a general reference. This runbook is the source of truth
for the current EventShare deployment: vps01 runs the application, IONOS remains the public
HTTPS gateway, and RabbitMQ/the standalone worker are intentionally deferred.

## 1. Current topology

~~~text
Browser
  |
  | HTTPS: eventshare.munashemudabura.com
  v
IONOS public IP 66.179.81.222
  |
  | IONOS host Nginx terminates TLS and proxies over Tailscale
  v
vps01 Tailscale IP 100.110.81.2:8088
  |
  v
vps01 container Nginx
  |                 |
  v                 v
Next.js frontend   Spring Boot API
                      |
                      v
                 PostgreSQL
~~~

The browser-visible domain does not need a DNS change because IONOS still owns the public IP and
TLS certificate. IONOS is now an edge gateway, not the application runtime.

The production Compose services are postgres, api, frontend, and nginx. Media processing currently
runs through the API's durable, in-process scheduler. RabbitMQ and the standalone worker remain in
the repository for a later migration and must not be started as part of this runbook.

## 2. Prerequisites

You need:

- Docker Engine and the Docker Compose plugin on vps01.
- Tailscale connectivity between IONOS and vps01.
- A GitHub repository checkout on vps01 at ~/apps/eventshare.
- An SSH key that can reach vps01. The private key stays on the operator's machine.
- Production credentials for PostgreSQL, Cloudflare R2, Clerk, and any enabled monitoring.
- Access to the GitHub repository's Actions runner settings for the one-time CD setup.

Check the server connection from Windows:

~~~powershell
ssh myvps
~~~

The local SSH alias is an operator convenience. GitHub Actions does not SSH into the server; the
self-hosted runner runs locally on vps01 and uses the server's Docker access.

## 3. First-time vps01 setup

### 3.1 Verify host dependencies

On vps01:

~~~bash
hostname
docker version
docker compose version
tailscale status
id
docker ps
~~~

The deployment user must be able to run Docker without interactive sudo. If this is a new host,
install Docker and add the deployment user to the docker group, then start a new login session.

### 3.2 Clone the repository

~~~bash
mkdir -p ~/apps
cd ~/apps
git clone https://github.com/mudabs/EventShare.git eventshare
cd ~/apps/eventshare
~~~

If the repository is private, configure an appropriate server-side Git credential or deploy key.
Do not put a personal access token in the repository or in this document.

### 3.3 Create the production environment file

~~~bash
cd ~/apps/eventshare
cp .env.example .env
chmod 600 .env
~~~

Edit .env on the server. At minimum, set the real production values for:

~~~text
APP_BASE_URL=https://eventshare.munashemudabura.com
NEXT_PUBLIC_APP_BASE_URL=https://eventshare.munashemudabura.com
NEXT_PUBLIC_API_BASE_URL=https://eventshare.munashemudabura.com/api
CORS_ALLOWED_ORIGINS=https://eventshare.munashemudabura.com
EVENTSHARE_TAILSCALE_IP=100.110.81.2
~~~

Also fill the database, Cloudflare R2, Clerk, and other variables documented in .env.example and
docs/ENVIRONMENT.md. Never commit .env, print it in logs, or paste its secrets into a ticket or
chat.

The frontend receives NEXT_PUBLIC_* values at image build time. A frontend URL change therefore
requires a rebuild and redeploy.

## 4. Initial database migration from IONOS

Use this section only when replacing an existing IONOS application with vps01. A new empty
deployment can skip it; Flyway will create the schema when the API starts.

### 4.1 Take a source backup and transfer it

Check the source container first:

~~~bash
ssh <old-ionos-ssh-target> 'docker ps --format "{{.Names}}" | grep eventshare-postgres'
~~~

For a direct, non-persistent transfer to vps01, stream the dump without creating a local file:

~~~bash
ssh <old-ionos-ssh-target> \
  'docker exec eventshare-postgres-1 pg_dump -U eventshare -d eventshare --no-owner --no-privileges' \
| ssh myvps \
  'docker exec -i eventshare-postgres-1 psql -U eventshare -d eventshare'
~~~

This assumes PostgreSQL is already running on vps01. Do not stop or delete the source database
until the target has been verified.

### 4.2 Handle an already-initialized target volume

PostgreSQL credentials are initialized when the volume is first created. If vps01 was started
with placeholder credentials, importing production data does not automatically change the
existing role password. Set the target eventshare role password interactively to the value used
by .env:

~~~bash
ssh myvps
cd ~/apps/eventshare
docker exec -it eventshare-postgres-1 psql -U eventshare -d eventshare
~~~

At the psql prompt, run the password command, then exit with \q. Never destroy a PostgreSQL
volume to fix a password problem when it contains production data.

### 4.3 Verify migrated data

Run read-only checks appropriate to the application:

~~~bash
docker exec eventshare-postgres-1 psql -U eventshare -d eventshare -c '\dt'
docker exec eventshare-postgres-1 psql -U eventshare -d eventshare -c 'select count(*) from users;'
docker exec eventshare-postgres-1 psql -U eventshare -d eventshare -c 'select count(*) from events;'
docker exec eventshare-postgres-1 psql -U eventshare -d eventshare -c 'select count(*) from media;'
~~~

Compare the results with the source backup or source database before cutting over public traffic.

## 5. Start the vps01 application

The production script names all Compose files so a developer's local override cannot
accidentally become the production topology:

~~~bash
cd ~/apps/eventshare
bash scripts/deploy-prod.sh
~~~

The script fast-forwards the checkout, builds the API and frontend images, starts the production
services, waits for PostgreSQL, checks the vps01 edge endpoint, and prunes unused Docker images.

Check service state and the local API:

~~~bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  -f docker-compose.override.yml ps

curl -fsSL http://100.110.81.2:8088/api/ping
~~~

Expected API response includes:

~~~json
{"status":"ok","service":"eventshare-api"}
~~~

## 6. Configure the IONOS public gateway

Keep the existing IONOS TLS certificate and public DNS record. Change the EventShare Nginx
upstream so it points to vps01 over Tailscale:

~~~nginx
proxy_pass http://100.110.81.2:8088;
~~~

The actual host configuration may be /etc/nginx/sites-available/eventshare.conf. After editing
it, test and reload Nginx:

~~~bash
sudo nginx -t
sudo systemctl reload nginx
~~~

Verify the public path before stopping the old IONOS application containers:

~~~bash
curl -fsSL https://eventshare.munashemudabura.com/api/ping
curl -I https://eventshare.munashemudabura.com/
~~~

Only after the public checks succeed should the old IONOS EventShare containers be stopped. Keep
their volumes and a copy of the old Nginx configuration for rollback.

## 7. Continuous Deployment setup

The repository contains two workflows:

- .github/workflows/ci.yml runs backend and frontend checks on pushes and pull requests.
- .github/workflows/deploy.yml deploys on vps01 after successful CI on main, and supports manual
  dispatch.

The deploy workflow runs on the unique self-hosted runner label eventshare-vps01. It does not copy
an SSH key or connect over SSH; it executes scripts/deploy-prod.sh on vps01 itself.

### 7.1 Register the runner once

In GitHub, open Repository Settings -> Actions -> Runners -> New self-hosted runner -> Linux x64.
Run GitHub's generated download and extraction commands on vps01. During configuration, use the
short-lived token supplied by GitHub:

~~~bash
./config.sh \
  --url https://github.com/mudabs/EventShare \
  --token <one-time-github-runner-token> \
  --name vps01-eventshare \
  --labels eventshare-vps01 \
  --work _work
~~~

Do not save the registration token in the repository. Install the runner as a service; the
standard Linux form is:

~~~bash
sudo ./svc.sh install munashe
sudo ./svc.sh start
~~~

Verify it:

~~~bash
systemctl is-enabled actions.runner.mudabs-EventShare.vps01-eventshare.service
systemctl is-active actions.runner.mudabs-EventShare.vps01-eventshare.service
~~~

The runner must be online in GitHub and able to run docker ps and git pull --ff-only in
~/apps/eventshare.

### 7.2 Verify the CD path

Push a normal change to main. GitHub should show:

~~~text
CI succeeds -> Deploy starts on vps01 -> deploy-prod.sh succeeds
~~~

A manual deployment can be started from the Deploy workflow's Run workflow button. If multiple
commits reach main quickly, the latest deployment supersedes an older in-progress deployment
through the deploy-main concurrency group.

## 8. Normal operating procedures

### Automatic release

Push or merge to main. No SSH action is required. The vps01 runner pulls the new commit and
rebuilds the affected images.

### Manual fallback from Windows

From the repository root:

~~~powershell
.\scripts\deploy-to-myvps.ps1
~~~

This uses the local myvps SSH alias and runs the same production deployment script on vps01.

### Inspect logs

~~~bash
cd ~/apps/eventshare
docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  -f docker-compose.override.yml logs -f api
~~~

Use frontend, nginx, or postgres in place of api as needed. Prometheus and Grafana are optional
and are not required for the core application deployment.

## 9. Rollback and recovery

The preferred application rollback is a normal Git revert so CI/CD remains the source of truth:

~~~bash
git checkout main
git pull --ff-only
git revert <bad-commit-sha>
git push origin main
~~~

The revert runs through CI and deploys automatically. Do not use git reset --hard on the
production checkout as a routine rollback method.

Database migrations are forward-only. Use expand-and-contract migrations so an older application
image can safely run during a rollback window. Never delete the PostgreSQL volume as a rollback
shortcut.

For a gateway rollback, restore the previous IONOS Nginx upstream, run nginx -t, reload Nginx,
and leave the vps01 data intact until the incident is understood.

## 10. Troubleshooting checklist

### Runner is offline

~~~bash
systemctl status actions.runner.mudabs-EventShare.vps01-eventshare.service --no-pager
journalctl -u actions.runner.mudabs-EventShare.vps01-eventshare.service -n 100 --no-pager
~~~

Then verify Tailscale, outbound HTTPS access, disk space, and Docker permissions.

### Deployment is pending

Check that the runner is online in GitHub, has the eventshare-vps01 label, the service is active,
no earlier deployment is holding the concurrency group, and ~/apps/eventshare exists with the
correct Git remote.

### API health check fails

~~~bash
docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  -f docker-compose.override.yml ps
docker compose \
  -f docker-compose.yml \
  -f docker-compose.prod.yml \
  -f docker-compose.override.yml logs --tail=200 api postgres nginx
curl -v http://100.110.81.2:8088/api/ping
~~~

Common causes are a PostgreSQL credential mismatch, an unhealthy database, or an incorrect
Tailscale address in .env/Compose.

### Public site fails but vps01 health succeeds

Check the IONOS Nginx upstream, Tailscale connectivity from IONOS to 100.110.81.2:8088, the TLS
certificate, and the DNS record. Do not change the application containers first if the local
vps01 health check is already passing.

## 11. Security rules

- Never commit .env, database dumps, runner tokens, personal access tokens, or private SSH keys.
- Keep .env readable only by the deployment user with chmod 600 .env.
- Do not copy a private SSH key to vps01 or IONOS. The local myvps key is for operator access; the
  GitHub runner uses its own registration credentials.
- Keep PostgreSQL private. Public traffic should enter through HTTPS and the intended
  Tailscale/Nginx path.
- Keep the old IONOS application stopped after cutover, but preserve its volumes until the new
  deployment has been backed up and accepted.

## 12. Files involved

- .github/workflows/ci.yml — build and test gates.
- .github/workflows/deploy.yml — automatic and manual CD trigger.
- scripts/deploy-prod.sh — server-side build, Compose restart, and health check.
- scripts/deploy-to-myvps.ps1 — Windows manual fallback through SSH.
- docker-compose.yml — base services.
- docker-compose.prod.yml — vps01/Tailscale edge binding.
- docker-compose.override.yml — vps01 resource and profile behavior.
- .env.example — documented configuration template.
- deploy/nginx/eventshare.conf — host Nginx template.
- docs/ENVIRONMENT.md — environment variable reference.

