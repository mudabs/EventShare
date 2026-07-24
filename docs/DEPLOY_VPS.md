# Legacy VPS bootstrap notes

This file predates the current host-Nginx + domain-based production deployment.
Use `docs/DEPLOYMENT.md` and `scripts/deploy-prod.sh` for the live deployment path.

The steps below are kept for historical reference only.

## 1. Copy the project to the VPS (from your laptop, PowerShell)

From the folder that contains the EventShare directory:

```powershell
cd C:\Users\mudab\Downloads\Projects
tar -a -c -f eventshare.zip --exclude="EventShare/frontend/node_modules" --exclude="EventShare/frontend/.next" --exclude="EventShare/backend/target" --exclude="EventShare/worker/target" EventShare
scp eventshare.zip <user>@<vps-ip>:~/
scp EventShare/scripts/vps-bootstrap.sh <user>@<vps-ip>:~/
```

## 2. Install Docker on the VPS (SSH in, run once)

```bash
ssh <user>@<vps-ip>
bash vps-bootstrap.sh
newgrp docker          # apply docker group without re-login
unzip -o eventshare.zip
cd EventShare
```

## 3. Point the app at the server IP

Your transferred .env still has localhost URLs. Rewrite them to the VPS IP in one go:

```bash
sed -i 's#http://localhost#http://<vps-ip>#g' .env
grep -E 'APP_BASE_URL|NEXT_PUBLIC|CORS_ALLOWED|BILLING' .env   # verify
```

Also set strong production passwords in .env (POSTGRES_PASSWORD + SPRING_DATASOURCE_PASSWORD
must match; RABBITMQ_DEFAULT_PASS + SPRING_RABBITMQ_PASSWORD must match; GRAFANA_ADMIN_PASSWORD).

## 4. Allow the new origin in Cloudflare R2 and Clerk

- R2 bucket -> Settings -> CORS Policy: add "http://<vps-ip>" to AllowedOrigins.
- Clerk dashboard -> your instance -> add http://<vps-ip> as an allowed origin/redirect.

## 5. Build and start

```bash
docker compose up -d --build
docker compose ps
docker compose logs --tail=60 api
```

First build takes several minutes. When api/postgres/rabbitmq are healthy, open
http://<vps-ip> in a browser.

## 6. Make yourself admin (same as local)

```bash
docker compose exec postgres psql -U eventshare -d eventshare -c "UPDATE users SET role='ADMIN';"
```

## Updating later

```bash
# re-copy the zip from your laptop, then on the VPS:
cd ~ && unzip -o eventshare.zip && cd EventShare
docker compose up -d --build
docker compose restart nginx
```

## HTTPS (needed for in-app camera + production Clerk)

Browsers only allow camera/mic on https (or localhost). A bare IP cannot get a public TLS
certificate. To enable HTTPS:
1. Point a domain's DNS A record at <vps-ip>.
2. Issue a cert with certbot (or Caddy) and put fullchain.pem/privkey.pem in infra/nginx/certs.
3. Enable the commented 443 server block in infra/nginx/conf.d (or switch to Caddy for auto-TLS).
4. Update the .env URLs to https://yourdomain and rebuild the frontend.
Until then, file uploads work over http but live camera capture will not on phones.
