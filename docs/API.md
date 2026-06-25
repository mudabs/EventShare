# API Reference

Base path: `/api` (proxied by nginx to the Spring Boot service on port 8080). Interactive
docs are served by springdoc at `/swagger-ui.html` with the OpenAPI document at
`/v3/api-docs`.

## Authentication

Host endpoints require a Clerk-issued JWT: `Authorization: Bearer <token>`. The frontend
obtains it from the Clerk session. Guest endpoints are public and authorized by the event
invite code (see ADR-003).

## Errors

Errors use RFC 7807 problem responses with an added stable `code`:

```json
{ "type": "about:blank", "title": "Bad Request", "status": 400,
  "detail": "Unsupported content type: image/tiff", "code": "bad_request" }
```

Validation failures return `code: "validation_error"` with a `fields` object. Rate-limited
requests return HTTP 429 with `code: "rate_limited"`.

## Endpoints

### Create event (host)

`POST /api/events`  Auth: Clerk JWT.

```json
{ "name": "Sam & Tari's Wedding", "eventType": "WEDDING",
  "description": "Our big day", "eventDate": "2026-08-15",
  "allowGuestDownloads": true, "autoApprove": true }
```

201 Response:

```json
{ "id": "uuid", "name": "Sam & Tari's Wedding", "eventType": "WEDDING",
  "status": "ACTIVE", "inviteCode": "K3M9PQ72RT",
  "inviteUrl": "https://eventshare.example.com/e/K3M9PQ72RT",
  "allowGuestDownloads": true, "autoApprove": true, "createdAt": "..." }
```

`eventType` is one of WEDDING, FAMILY, GRADUATION, CHURCH, CONFERENCE, BIRTHDAY, REUNION,
OTHER.

### Get event / analytics (host)

`GET /api/events/{id}`  Auth: Clerk JWT, must be the host. Returns the event.

`GET /api/events/{id}/analytics`  Auth: Clerk JWT, must be the host.

```json
{ "eventId": "uuid", "memberCount": 42, "mediaTotal": 311,
  "visibleCount": 305, "hiddenCount": 6, "archivedCount": 0, "totalBytes": 1288490188 }
```

### Public event summary (guest)

`GET /api/events/code/{code}`  Public. Minimal, non-sensitive view for the join page.

```json
{ "name": "Sam & Tari's Wedding", "eventType": "WEDDING",
  "active": true, "allowGuestDownloads": true }
```

### Join event (guest)

`POST /api/events/code/{code}/join`  Public. Body `{ "displayName": "Alice" }`.

```json
{ "membershipId": "uuid", "eventId": "uuid", "inviteCode": "K3M9PQ72RT",
  "eventName": "Sam & Tari's Wedding", "displayName": "Alice" }
```

### Request upload URL (guest)

`POST /api/media/upload-url`  Public.

```json
{ "inviteCode": "K3M9PQ72RT", "filename": "sunset.jpg",
  "contentType": "image/jpeg", "sizeBytes": 2384122,
  "uploaderDisplayName": "Alice", "membershipId": "uuid" }
```

Response. Upload the bytes with an HTTP PUT to `uploadUrl`, setting `Content-Type` to
`requiredContentType`.

```json
{ "mediaId": "uuid", "objectKey": "events/.../originals/.../sunset.jpg",
  "uploadUrl": "https://<account>.r2.cloudflarestorage.com/...signed...",
  "httpMethod": "PUT", "requiredContentType": "image/jpeg", "expiresInSeconds": 900 }
```

### Complete upload (guest)

`POST /api/media/{mediaId}/complete`  Public. Idempotent.

```json
{ "sha256": "<64-hex>", "width": 4032, "height": 3024 }
```

Confirms the object in R2, records the hash and size, runs exact duplicate detection, and
publishes the processing event. Returns the media object with a signed `originalUrl`.

### Gallery (guest)

`GET /api/events/code/{code}/media?cursor={opaque}&limit={1..100}`  Public. Newest first.

```json
{ "items": [ { "id": "uuid", "mediaType": "PHOTO", "status": "PROCESSED",
    "moderationState": "VISIBLE", "uploaderDisplayName": "Alice",
    "width": 4032, "height": 3024, "duplicate": false, "createdAt": "...",
    "originalUrl": "https://...signed...", "thumbnailUrl": "https://...signed..." } ],
  "nextCursor": "b64cursor", "hasMore": true }
```

### System

`GET /api/ping`  Public liveness. Actuator probes are at `/actuator/health/liveness` and
`/actuator/health/readiness`; Prometheus metrics at `/actuator/prometheus` (internal only).

## V2 endpoints

My Events and membership: POST /api/me/events/join, GET /api/me/events,
DELETE /api/me/events/{eventId}/membership, GET /api/events/{eventId}/members (owner),
DELETE /api/events/{eventId}/members/{membershipId} (owner), GET /api/me/profile.

Dashboards: GET /api/me/dashboard, GET /api/events/{eventId}/dashboard (owner).

Moderation and settings: GET /api/events/{eventId}/media (owner, by state),
PATCH /api/events/{eventId}/media/{mediaId}/moderation, DELETE
/api/events/{eventId}/media/{mediaId}/permanent, GET and PATCH /api/events/{eventId}/settings.

Plans and billing: GET /api/plans (public), GET /api/me/subscription,
POST /api/billing/checkout-session, POST /api/billing/portal-session,
POST /api/billing/webhook (Stripe-signed, public).

Promo and whitelist: POST /api/me/promo/redeem, and admin GET/POST /api/admin/promo-codes,
POST /api/admin/promo-codes/{id}/disable, GET/POST /api/admin/whitelist,
DELETE /api/admin/whitelist/{id}.

Admin: GET /api/admin/users, GET /api/admin/users/{id}, POST /api/admin/users/{id}/disable and
/enable, DELETE /api/admin/users/{id}, POST /api/admin/users/{id}/subscription,
GET /api/admin/events, POST /api/admin/events/{id}/archive, DELETE /api/admin/events/{id},
POST /api/admin/events/{id}/transfer, GET /api/admin/analytics. All admin endpoints require the
caller's persisted role to be ADMIN.
