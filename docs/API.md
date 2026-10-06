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
  "active": true, "allowGuestDownloads": true, "guestUploadsEnabled": true }
```

### Join event (guest)

`POST /api/events/code/{code}/join`  Public. Body `{ "displayName": "Alice" }`.

```json
{ "membershipId": "uuid", "eventId": "uuid", "inviteCode": "K3M9PQ72RT",
  "eventName": "Sam & Tari's Wedding", "displayName": "Alice" }
```

Note: `GET /api/events/code/{code}` (public event summary) includes `zipDownloads`: true
when the host's plan offers ZIP downloads (paid plans and unlimited accounts). The guest
gallery hides "Download all" and builds ZIPs only when it is true.

### Request upload URL (guest)

`POST /api/media/upload-url`  Public.

```json
{ "inviteCode": "K3M9PQ72RT", "filename": "sunset.jpg",
  "contentType": "image/jpeg", "sizeBytes": 2384122,
  "uploaderDisplayName": "Alice", "membershipId": "uuid" }
```

`membershipId` is optional. When present it must be an ACTIVE membership of this event
(403 otherwise), and the stored uploader name is taken from the membership instead of
`uploaderDisplayName`. Plan limits are checked here (403 with code `quota_exceeded`). For the
public demo, the API also enforces `DEMO_GUEST_UPLOADS_ENABLED`,
`DEMO_MAX_GUEST_UPLOADS`, `DEMO_MAX_GUEST_UPLOADS_PER_IP` and `DEMO_MAX_GUEST_UPLOAD_BYTES`
before creating the reservation; the defaults are 25 MB per file, 8 uploads per reset in total
and 4 per client IP. For demo events, the public event summary also returns
`demoUploadsRemaining` (for the calling visitor) and `demoMaxUploadBytes`; both are `null`
for every other event.

Response. Upload the bytes with an HTTP PUT to `uploadUrl`, setting `Content-Type` to
`requiredContentType`. The body must be exactly `sizeBytes` long: the URL signs
`Content-Length`, so R2 returns 403 for any other size.

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

Confirms the object in R2, records the hash and real size, runs exact duplicate detection,
and leaves the row UPLOADED for the in-process processor. Returns the media object with a
signed `originalUrl`. If the stored object is larger than the declared `sizeBytes`, the
object is deleted, the media is marked FAILED/DELETED, and the call returns 400 with code
`upload_rejected`.

### Gallery (guest)

`GET /api/events/code/{code}/media?cursor={opaque}&limit={1..100}`  Public. Newest first.
Optional header `X-Membership-Id: <uuid>`; when sent, each item's `ownedByRequester` is true
for media uploaded by that membership.

```json
{ "items": [ { "id": "uuid", "mediaType": "PHOTO", "status": "PROCESSED",
    "moderationState": "VISIBLE", "uploaderDisplayName": "Alice",
    "width": 4032, "height": 3024, "duplicate": false, "createdAt": "...",
    "originalUrl": "https://...signed...", "thumbnailUrl": "https://...signed...",
    "downloadUrl": "https://...signed, Content-Disposition: attachment...",
    "ownedByRequester": false } ],
  "nextCursor": "b64cursor", "hasMore": true }
```

### Delete own media (guest)

`DELETE /api/events/code/{code}/media/{mediaId}`  Public.

```json
{ "membershipId": "uuid", "displayName": "Alice" }
```

`membershipId` is required and must match the media's uploader membership and be ACTIVE;
otherwise 403. `displayName` is ignored for authorisation (kept for older clients). The
media is soft-deleted (moderation state DELETED). Returns 204 No Content.

### Demo mode

`GET /api/demo/info`  Public. `{ "enabled": false }` unless demo mode is on; otherwise also
`inviteCode`, `secondaryInviteCode`, `promoCode`, `guestUrl`, `resetCron`, `resetZone`, and,
only when `DEMO_SHOW_CREDENTIALS=true`, `logins: [{ role, email, password }]`.

`POST /api/admin/demo/reset`  Admin. Deletes all demo-owned data and seeds it again.
Returns `{ trigger, finishedAt, photosSeeded, oldObjectsRemoved, durationMs }`. 404 when
demo mode is off. See `docs/DEMO.md`.

### System

`GET /api/ping`  Public liveness. Actuator probes are at `/actuator/health/liveness` and
`/actuator/health/readiness`; Prometheus metrics at `/actuator/prometheus` (internal only).

## V2 endpoints

My Events and membership: POST /api/me/events/join (returns the membership:
`{ membershipId, eventId, inviteCode, eventName, displayName }`), GET /api/me/events,
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
