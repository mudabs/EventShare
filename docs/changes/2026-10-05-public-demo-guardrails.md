# 2026-10-05: Public demo guardrails

| Field | Value |
|---|---|
| Date | 2026-10-05 |
| Author | Munashe Mudabura, with Codex (AI pair programmer) |
| Scope | Demo upload policy, demo-host ownership policy, public event response, documentation |
| Schema migration | `V10__demo_upload_guardrails.sql` adds `media.demo_seeded` and an event/marker index |
| Breaking changes | Demo host can no longer create arbitrary events; public demo uploads default to 8 reservations per reset and 25 MiB per file |
| Build status at hand-off | Frontend `npm run typecheck` passes. Backend `mvn verify` could not run because Maven is not installed/on PATH in the authoring environment |

## Why

The public demo shares infrastructure with the real paid product. The demo host was
whitelisted for unlimited plan limits, which also meant the shared credentials could create
unlimited events and upload unlimited media. Recruiters should be able to explore the product,
but the demo must not become an unapproved event-hosting account.

## Summary

| Id | Change |
|---|---|
| DG1 | Added configurable public-demo upload switch, reset-scoped reservation cap, and 25 MiB default per-file cap |
| DG2 | Enforced the demo upload policy in the upload reservation API before creating a media row or presigned URL |
| DG3 | Restricted the demo host from creating events and from owner operations on anything except the two seeded demo invite codes |
| DG4 | Added an explicit seeded-media marker and Flyway migration so seeded showcase photos never consume the allowance |
| DG5 | Added public response/UI state for a read-only demo and documented environment variables, reset semantics, and Stripe compatibility |

## Details

`DEMO_GUEST_UPLOADS_ENABLED` defaults to true, with `DEMO_MAX_GUEST_UPLOADS=8` and
`DEMO_MAX_GUEST_UPLOAD_BYTES=26214400`. Setting the switch false leaves the gallery visible but
returns a forbidden response from the API and removes the upload/join controls from the public
event page. Reservations remain counted even after a guest deletes media, which closes the
obvious upload/delete bypass. The nightly seeder deletes the demo events and their media, so
the counter naturally returns to zero after each reset.

The demo host remains a real Clerk account and remains whitelisted so the host dashboard and
paid-plan/ZIP demo continue to work. Stripe checkout, subscriptions, and customer data are not
changed by this feature.

## Files changed

- Backend: `DemoProperties`, `DemoGuard`, `DemoSeeder`, `Media`, `MediaRepository`,
  `MediaService`, owner services, `EventService`, `PublicEventResponse`, `application.yml`.
- Frontend: public event type and read-only state.
- Configuration/docs: `.env.example`, `.env.demo.example`, `docs/DEMO.md`,
  `docs/ENVIRONMENT.md`, `docs/DECISIONS.md`.
- Database: `backend/src/main/resources/db/migration/V10__demo_upload_guardrails.sql`.

## Tests and verification

The final hand-off must run:

```powershell
cd backend
mvn verify
cd ..\frontend
npm run typecheck
```

Manual production verification after deployment:

1. Open the seeded guest link and confirm the public event still loads.
2. Upload one small image successfully.
3. Repeat until the configured cap is reached and confirm the API returns the cap message.
4. Try a file over 25 MiB and confirm no upload reservation is created.
5. Sign in as the demo host and confirm the two seeded events are manageable but creating a
   third event returns `403 forbidden`.
6. Leave Stripe checkout untouched and verify an ordinary signed-in customer can still reach it.

## Rollback

Set `DEMO_GUEST_UPLOADS_ENABLED=false` for an immediate read-only public demo. To remove the
code change, deploy the previous application only after preserving the migration; the new
boolean column is harmless to an older application, but the old app will not enforce the
guardrails. A complete schema rollback would require a new, explicitly approved destructive
migration and is not recommended.

## Addendum: review fixes (DG6 to DG11)

Applied after a review of DG1 to DG5, before anything was committed or deployed.

| Id | Problem found in review | Fix |
|---|---|---|
| DG6 | One visitor could use all 8 shared uploads, leaving the demo dead for everyone until the reset | Per-IP cap (`DEMO_MAX_GUEST_UPLOADS_PER_IP`, default 4) inside the shared total. A salted SHA-256 of the client IP is stored in the new `media.uploader_ip_hash`, only for demo-event uploads (deleted nightly); counted under the same host-row lock as the shared cap |
| DG7 | The shared demo login could delete the seeded events, rename them (shown publicly), change the cover, and permanently delete photos | `DemoGuard` blocks event deletion, name and cover changes, and permanent media deletion on demo events. Hide/restore and display toggles still work |
| DG8 | The shared demo login could open Stripe checkout and the billing portal | `BillingService.createCheckout` and `createPortal` reject the demo host and demo admin. Promo redemption stays (part of the walkthrough; reset clears it) |
| DG9 | Visitors only learned the cap was reached after picking a file; the size error showed raw bytes | Public event summary returns `demoUploadsRemaining` (per visitor) and `demoMaxUploadBytes`; the page shows "N uploads left, up to 25 MB each", trims oversize or excess files before uploading, and shows a clear message at 0. API errors now say "25 MB" |
| DG10 | `docker-compose.yml` did not pass the new `DEMO_*` variables, so the off switch and limits were ignored in Docker. V10 left `demo_seeded DEFAULT TRUE` permanently, so non-JPA inserts would bypass the cap | Variables added to the `api` environment. New `V11__demo_guardrails_followup.sql` sets the default to `FALSE` (existing rows keep their backfill) and adds `uploader_ip_hash` with a partial index |
| DG11 | Found while adding DG6: the container nginx overwrote `X-Real-IP`/`X-Forwarded-For` with `$remote_addr`, which in production is the IONOS gateway's Tailscale address. Every visitor reached the API with the same IP, so the existing per-IP rate limiting was effectively global and a per-IP cap would have been too | `infra/nginx/conf.d/default.conf` uses the real-IP module: trusts loopback, RFC 1918 and Tailscale (100.64.0.0/10) peers, reads `X-Forwarded-For` with `real_ip_recursive on`, so `$remote_addr` becomes the right-most untrusted (real) client address and cannot be spoofed by a client-supplied header |

V10 was left unchanged (it may already have run on a local database); all schema changes are
in V11.

### Tests added or changed

- `DemoGuardTest` (13 cases): event creation; manage scope; delete/rename/cover/purge blocked
  on demo events and allowed on real ones; billing blocked for demo host and admin; upload
  under both caps allowed and returns a 64-char hash (never the raw IP); shared cap; per-IP
  cap blocks one visitor but not another; "25 MB" message; read-only switch; non-demo events
  never limited or tracked; remaining = min(shared, per-IP); MB formatting.
- `MediaServiceTest`: lock, then demo guard, then plan limits, then save (order verified);
  the IP hash is stored on the reservation; a rejected demo upload reserves nothing and
  presigns nothing.
- `DemoUploadCountIT` (Testcontainers): deleted uploads still count, seeded photos never
  count, per-IP counts are isolated, and a raw SQL insert defaults to `demo_seeded = FALSE`.
- `DemoPropertiesTest`: per-IP default of 4.

### Files

Backend: `demo/DemoGuard.java`, `demo/DemoProperties.java`, `demo/DemoSeeder.java`
(`/api/demo/info` adds `maxGuestUploadsPerIp`), `media/Media.java`, `media/MediaRepository.java`,
`media/MediaService.java`, `media/MediaModerationService.java`, `event/EventService.java`,
`event/EventController.java`, `event/EventSettingsService.java`,
`event/dto/PublicEventResponse.java`, `billing/BillingService.java`, `application.yml`,
`db/migration/V11__demo_guardrails_followup.sql`.
Frontend: `app/e/[code]/page.tsx`, `components/UploadButton.tsx`, `lib/types.ts`.
Ops: `docker-compose.yml`, `infra/nginx/conf.d/default.conf`, `.env.example`, `.env.demo.example`.
Docs: this addendum, `docs/DEMO.md`, `docs/ENVIRONMENT.md`, `docs/API.md`.

### Verify after deploy

1. `docker compose exec api env | grep DEMO_` shows the four guardrail variables.
2. From two different networks (for example Wi-Fi and phone data), each can upload 4 photos;
   a fifth from the same network is refused with "You have used your 4 demo uploads"; the
   page then shows the "used all your demo uploads" message.
3. A 30 MB file is refused in the browser before upload ("limited to 25 MB per file").
4. As `demo-host`: deleting either demo event, renaming it, changing its cover, permanently
   deleting a photo, and opening checkout all return 403; hiding and restoring a photo works.
5. API logs show varied client IPs (not the gateway address) for rate-limit and upload
   entries.

Build status: frontend `tsc --noEmit` passes; Java files parse; the backend build and tests
were not run in the authoring sandbox (Maven Central blocked) and the nginx config was not
syntax-checked (no nginx/Docker there). Run `mvn verify` and `docker compose exec nginx nginx -t`.
