# Environment Variables

Copy `.env.example` to `.env` for Docker Compose. The frontend additionally reads
`frontend/.env.local` in local development (see `.env.local.example`). Never commit real
secrets.

## Core URLs

APP_BASE_URL. Public base URL of the app; used to build invite links. Used by: api.
NEXT_PUBLIC_APP_BASE_URL. Same value exposed to the browser. Used by: frontend.
NEXT_PUBLIC_API_BASE_URL. Base URL the browser uses for API calls, for example
`https://host/api`. Used by: frontend.
CORS_ALLOWED_ORIGINS. Comma-separated origins allowed to call the API. Used by: api.
EVENTSHARE_TAILSCALE_IP. Tailscale IPv4 address used for the production Nginx binding on vps01;
defaults to `100.110.81.2`.

## PostgreSQL

POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD. Database name and credentials for the
postgres container.
SPRING_DATASOURCE_URL. JDBC URL, for example `jdbc:postgresql://postgres:5432/eventshare`.
Used by: api.
SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD. Must match the POSTGRES_* values.
Used by: api.

RabbitMQ and the standalone worker are deferred. Their connection variables should be added
when that architecture stage is reintroduced; they are not required by the current vps01
deployment.

## Cloudflare R2

R2_ACCOUNT_ID. Your Cloudflare account id.
R2_ENDPOINT. `https://<account-id>.r2.cloudflarestorage.com`. Used by: api.
R2_REGION. Use `auto`. Used by: api.
R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY. R2 API token credentials. Used by: api.
R2_BUCKET. Bucket name, for example `eventshare-media`. Used by: api.
R2_PRESIGN_UPLOAD_TTL_SECONDS, R2_PRESIGN_DOWNLOAD_TTL_SECONDS. Lifetimes of signed URLs.
Used by: api.

## Clerk

CLERK_ISSUER. Token issuer, for example `https://<subdomain>.clerk.accounts.dev`. Used by:
api (issuer validation).
CLERK_JWKS_URL. JWKS endpoint, usually `${CLERK_ISSUER}/.well-known/jwks.json`. Used by: api.
CLERK_AUDIENCE. Optional; set only if you configure an `aud` claim. Used by: api.
NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY. Clerk publishable key (browser). Used by: frontend.
CLERK_SECRET_KEY. Clerk secret key (server). Used by: frontend server runtime.
NEXT_PUBLIC_CLERK_SIGN_IN_URL, NEXT_PUBLIC_CLERK_SIGN_UP_URL. Route paths, default
`/sign-in` and `/sign-up`. Used by: frontend.

## Media limits

MEDIA_MAX_UPLOAD_BYTES. Global per-file size cap in bytes (default 500 MB). Used by: api.
MEDIA_ALLOWED_CONTENT_TYPES. Comma-separated allowed MIME types. Used by: api.

## Observability and runtime

GRAFANA_ADMIN_USER, GRAFANA_ADMIN_PASSWORD. Grafana admin login.
JAVA_OPTS. JVM flags for api, for example `-XX:MaxRAMPercentage=70`.

## Platform administration

ADMIN_EMAILS, ADMIN_CLERK_USER_IDS. Accounts auto-granted ADMIN at sign-in. Used by: api.
DEMO_EMAILS. Emails that always get unlimited plan limits. Used by: api.

## Interview demo mode

All optional; see `docs/DEMO.md`. Used by: api. Off unless `DEMO_ENABLED=true`.

DEMO_ENABLED. Master switch for seeding, nightly reset, and the demo endpoints.
DEMO_HOST_USERNAME, DEMO_HOST_PASSWORD, DEMO_HOST_NAME. Demo host login (username default
`demo-host`). The password has no default; the API sets it on the Clerk account at startup
and at every reset.
DEMO_HOST_EMAIL. Optional. Leave blank for a username-only login; set it only if your Clerk
instance requires an email.
DEMO_ADMIN_ENABLED, DEMO_ADMIN_USERNAME, DEMO_ADMIN_EMAIL, DEMO_ADMIN_PASSWORD,
DEMO_ADMIN_NAME. Optional demo platform admin (username default `demo-admin`). Keep
`DEMO_ADMIN_ENABLED=false` on a site with real users.
DEMO_INVITE_CODE, DEMO_SECONDARY_INVITE_CODE. Fixed invite codes of the two demo events
(default `EVENTSHARE`, `TEAMDAY26X`).
DEMO_PROMO_CODE. Demo promo code granting Wedding Pro for 30 days (default `INTERVIEW30`).
DEMO_PHOTO_COUNT. Generated photos in the main event (default 18, max 60).
DEMO_RESET_CRON, DEMO_RESET_ZONE. Spring cron and zone for the automatic reset (default
`0 0 4 * * *`, `America/Chicago`).
DEMO_SHOW_CREDENTIALS. When true, `GET /api/demo/info` and the landing page show the demo
logins.
EVENTSHARE_HTTP_PORT. Host port for the app's nginx on 127.0.0.1 (default 8088; the local
demo uses 8090). Used by: docker compose.

## CI/CD

VPS_APP_DIR. Optional environment variable on the `vps01` self-hosted runner; defaults to
`$HOME/apps/eventshare`. The deploy workflow no longer targets the IONOS host or its public IP.
