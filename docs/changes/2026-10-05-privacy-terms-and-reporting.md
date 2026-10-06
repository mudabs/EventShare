# 2026-10-05: Privacy Policy, Terms of Use, consent notes and photo reporting

| Field | Value |
|---|---|
| Date | 2026-10-05 |
| Author | Munashe Mudabura, with Claude (AI pair programmer) |
| Base commit | `be05bde` (demo guardrails) plus the uncommitted analytics chart fix |
| Scope | New `/privacy` and `/terms` pages, site footer, consent notes, photo reporting (API, DB, UI), audit IP retention |
| Schema migration | `V12__media_reports_and_privacy.sql` (new `media_reports` table) |
| New configuration | `NEXT_PUBLIC_CONTACT_EMAIL` (frontend build arg), `AUDIT_IP_RETENTION_DAYS` (API, default 90) |
| Build status at hand-off | Frontend `tsc --noEmit` passes. Java files parse; not compiled or tested here (Maven Central blocked). Run `mvn verify` |

Not legal advice: the texts are written to match what the code does, in plain language, and
should be reviewed by a lawyer before the service takes paying customers.

## Why

The app collects personal data (names, emails, photos of people including children, IP
addresses) and takes payments, so it needs a public Privacy Policy (CalOPPA for California
visitors; GDPR/UK GDPR for European guests; Stripe and Clerk terms) and Terms of Use (content
licence, uploader warranties, prohibited content, DMCA process). Guests upload photos of other
people, so there must also be a simple way to ask for a photo to be removed.

## What changed

| Id | Change |
|---|---|
| PT1 | `/privacy` and `/terms` pages (`frontend/src/app/privacy`, `frontend/src/app/terms`), shared `LegalPage` layout, facts in `frontend/src/lib/legal.ts` |
| PT2 | Site-wide `SiteFooter` in the root layout (Privacy, Terms, Pricing); the landing page's own footer was folded into it |
| PT3 | `ConsentNote` one-liners: guest join (`JoinPrompt`, `AuthedEventJoin`), upload (`UploadButton`), and event creation (`CreateEventForm`, host wording) |
| PT4 | Photo reporting: `POST /api/events/code/{code}/media/{id}/report` (public, 204). One report per visitor per item (salted IP hash, no raw IP), 10 per minute per IP, item must be visible. Three open reports, or one child-safety report, set the item to HIDDEN until the host reviews it. Restoring an item marks its reports reviewed |
| PT5 | Owner gallery shows a "Reported ×N" badge (`MediaResponse.reportCount`, owner views only) |
| PT6 | `AuditIpRetentionJob`: daily, clears `audit_logs.ip_address` older than `AUDIT_IP_RETENTION_DAYS` (default 90), the promise made in the Privacy Policy |
| PT7 | `NEXT_PUBLIC_CONTACT_EMAIL` wired through `docker-compose.yml` build args and the frontend `Dockerfile`; when empty, the pages point to the Report button and the operator's website instead |

## Policy statements and the code behind them

Keep this table true. When data collection, providers or retention change, update
`/privacy` (and this table) in the same change.

| Statement on `/privacy` | Where it is true in the code |
|---|---|
| Account name, email, avatar via Clerk; password never seen | `CurrentUserService`, `users` table |
| Guest display name; browser keeps a guest identifier | `event_memberships.guest_display_name`; `frontend/src/store/guestStore.ts` (localStorage) |
| Files kept as uploaded, including embedded metadata (GPS) | Originals in R2 are never rewritten; only thumbnails are generated |
| Fingerprint for duplicates | `media.sha256` |
| Raw IP with security actions, cleared after 90 days | `audit_logs.ip_address`, `AuditIpRetentionJob` |
| Hashed IPs for visitor counts and fair-use limits | `event_visits.visitor_key` (hash of IP and user agent), `media.uploader_ip_hash` (demo), `media_reports.reporter_ip_hash` |
| Stripe customer reference only, no card data | `users.stripe_customer_id`, `subscriptions` |
| Gallery visible to anyone with the invite link | Capability model, ADR-003 |
| Only essential cookies and storage | Clerk session cookies, guest store, service worker cache; no analytics or ad scripts |
| No facial recognition | None implemented; roadmap item must add opt-in consent first |
| Demo wiped nightly | `DemoSeeder` / `DemoLifecycle` |
| Deleted items and events erased after 30 days | `Media.setModerationState` records `deleted_at`; `MediaPurgeJob` (daily) erases R2 objects and rows after `MEDIA_PURGE_DAYS` (see 2026-10-05-media-purge-admin-chart-demo-promo.md) |

## Operator to-dos (not code)

1. Set `NEXT_PUBLIC_CONTACT_EMAIL` in the server `.env` (an address you read) and rebuild the
   frontend. It appears on both pages and is the DMCA agent contact.
2. Register a DMCA designated agent with the U.S. Copyright Office (dmca.copyright.gov),
   $6, renew every 3 years, using the same name and contact.
3. Optional: in the Clerk dashboard, enable the legal-consent checkbox on sign-up and link it
   to `/terms` and `/privacy`.
4. Have a lawyer review both pages before taking payments; the refund wording is deliberately
   neutral.

## Tests

`MediaReportServiceTest`: report stored with a 64-character IP hash and trimmed note; third
open report hides the item; a child-safety report hides immediately; repeat report from the
same visitor is ignored without saving; hidden or unknown items return 404; rate limit.

## Files

Backend: `report/` (ReportReason, MediaReport, MediaReportRepository, MediaReportService,
MediaReportController, dto/ReportMediaRequest), `privacy/AuditIpRetentionJob.java`,
`media/MediaModerationService.java`, `media/dto/MediaResponse.java`,
`config/SecurityConfig.java`, `application.yml`, `db/migration/V12__media_reports_and_privacy.sql`.
Frontend: `app/privacy/page.tsx`, `app/terms/page.tsx`, `app/layout.tsx`, `app/page.tsx`,
`components/LegalPage.tsx`, `components/SiteFooter.tsx`, `components/ReportDialog.tsx`,
`components/Gallery.tsx`, `components/OwnerGallery.tsx`, `components/JoinPrompt.tsx`,
`components/AuthedEventJoin.tsx`, `components/UploadButton.tsx`,
`components/CreateEventForm.tsx`, `lib/legal.ts`, `lib/api.ts`, `lib/types.ts`, `Dockerfile`.
Ops: `docker-compose.yml`, `.env.example`, `.env.demo.example`, `frontend/.env.local.example`.

## Verify

1. Footer on every page links to Privacy and Terms; both load without signing in.
2. Join, upload and create-event forms show the one-line agreement note.
3. Open a photo you did not upload: "Report" appears; send a report; toast confirms.
4. Report the same photo from three different networks (or once as "Child safety concern"):
   it disappears from the guest gallery and shows "Reported ×N" in the host's Hidden tab.
   Restore it: the badge clears.
5. After 90 days, `SELECT count(*) FROM audit_logs WHERE ip_address IS NOT NULL AND
   created_at < now() - interval '90 days'` returns 0.
