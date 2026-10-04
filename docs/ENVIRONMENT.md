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

## CI/CD

VPS_APP_DIR. Optional environment variable on the `vps01` self-hosted runner; defaults to
`$HOME/apps/eventshare`. The deploy workflow no longer targets the IONOS host or its public IP.
