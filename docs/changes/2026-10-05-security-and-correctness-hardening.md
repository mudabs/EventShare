# 2026-10-05: Security and correctness hardening

| Field | Value |
|---|---|
| Date | 2026-10-05 |
| Author | Munashe Mudabura, with Claude (AI pair programmer) |
| Base commit | `51b99f2` (fix: allow Clerk Turnstile frames) |
| Scope | Backend media upload, delete, processing; guest gallery (web, mobile); docs |
| Schema migration | None. No Flyway migration was added or changed |
| Breaking API changes | Yes, two small ones (see "Compatibility") |
| Build status at hand-off | Frontend `tsc --noEmit` passes. Backend Java files parse cleanly but were **not compiled or tested** here (no Maven Central access in the authoring sandbox). Run `cd backend && mvn verify` before merging |

## Why this change exists

A full repository review found eight issues. Five were real bugs in the upload and
processing path; three were documentation and hygiene problems. This document records
each issue, what was changed, and how to check it, so anyone (human or AI model) picking
up the repo later can follow the reasoning without the original conversation.

Change ids C1 to C8 are referenced from code comments, tests and ADRs. To find every
location a change touched, search for its id, for example
`grep -rnw "C3" backend/src frontend/src mobile`.

## Summary

| Id | Severity | Problem | Fix |
|---|---|---|---|
| C1 | High | Upload size not enforced by storage: declare 1 MB, PUT 5 GB | Presigned PUT signs `Content-Length`; completion rejects objects larger than declared |
| C2 | High | Any guest could delete any media by typing the uploader's public display name | Self-delete requires the uploader's ACTIVE membership id; UI uses server-computed `ownedByRequester` |
| C3 | Medium | Plan limits checked outside the insert transaction, so parallel uploads could exceed quota | Lock host user row `FOR UPDATE`, check limits and insert in one transaction |
| C4 | Medium | `membershipId` on upload was stored unvalidated; uploader name was client-chosen | Membership must be ACTIVE in the same event; name copied from membership |
| C5 | Medium (latent) | Processing poller unsafe with more than one API replica | Atomic claim with `FOR UPDATE SKIP LOCKED` in `MediaWorkQueue` |
| C6 | Low | `ARCHITECTURE.md` described the retired RabbitMQ worker; README roadmap listed shipped features | Rewrote architecture doc, added ADR-013 to ADR-016, updated README and API docs |
| C7 | Low | Five overlapping deployment docs, unclear which is canonical | Added `docs/README.md` index naming the runbook as canonical |
| C8 | Low | Six files showed as modified only because of CRLF line endings | Added `.gitattributes`; ignored `*.tsbuildinfo` |

## Details

### C1: Enforce upload size at the storage layer

Problem. `R2StorageService.presignUpload` signed only bucket, key and content type. The API
validated `sizeBytes` from the request, but nothing tied the actual upload to that number.
`completeUpload` then overwrote `sizeBytes` with R2's real size without rejecting it, so
storage quota and cost could be bypassed.

Change.

- `R2StorageService.presignUpload(objectKey, contentType, contentLength)` now sets
  `contentLength` on the `PutObjectRequest`, which puts `content-length` into the SigV4
  signed headers. R2 answers 403 if the body size differs.
- `MediaService.completeUpload` compares R2's `HEAD` size with the declared size. If the
  object is larger, `rejectOversizeUpload` deletes the object, marks the row `FAILED` +
  `DELETED` (so it stops counting toward quota), writes audit action
  `MEDIA_UPLOAD_REJECTED`, increments `eventshare.media.upload.rejected`, and throws the new
  `UploadRejectedException` (400, code `upload_rejected`).
- `completeUpload` is now `@Transactional(noRollbackFor = UploadRejectedException.class)` so
  the FAILED/DELETED marking survives the exception.
- Mobile: `mobile/app/event/[code].tsx` now declares `bytes.byteLength` (the exact bytes it
  will PUT) instead of the picker's `fileSize`, which could differ and would now fail the
  signature.

Why both layers. The signature is the real control. The completion check is defence in
depth in case a storage provider ignores the signed length.

### C2: Guest self-delete authorised by membership id only

Problem. `deleteOwnMedia` allowed the delete if the membership id matched **or** if the
request's `displayName` matched the stored uploader name, case-insensitively. Uploader names
are visible in the gallery, so any guest could delete anyone's photos. The web UI also
decided whether to show "Delete" by comparing names.

Change.

- `DeleteOwnMediaRequest.membershipId` is now `@NotNull`. `displayName` is still accepted
  but ignored for authorisation.
- `MediaService.deleteOwnMedia` requires `media.uploaderMembershipId == request.membershipId`
  and that membership to be ACTIVE in this event. The name fallback is removed. The audit
  label now comes from the membership record.
- `MediaResponse` gained `ownedByRequester` (default `false`, plus `withOwnedByRequester`).
- `GET /api/events/code/{code}/media` accepts an optional `X-Membership-Id` header; the
  service sets `ownedByRequester` per item. The membership id is never returned.
- CORS allowed headers now include `X-Membership-Id` (`SecurityConfig`).
- Web: `fetchGallery(code, cursor, limit, membershipId)` sends the header; `Gallery.tsx`
  shows Delete only when `ownedByRequester` is true; the gallery query key includes the
  membership id so flags refresh after joining; `isSameName` was removed.

### C3: Race-safe plan limit enforcement

Problem. `MediaController` called `PlanLimitService.checkCanUpload` in its own read-only
transaction, then `MediaService.requestUploadUrl` inserted the row in another. Ten parallel
requests could all read "499 of 500 photos" and all insert.

Change.

- New `UserRepository.findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`, emits `SELECT ... FOR
  UPDATE`).
- `MediaService.requestUploadUrl` locks the event host's user row, calls
  `checkCanUpload`, and inserts the PENDING row, all in one transaction. The controller no
  longer does the check.
- `PlanLimitService.checkCanUpload` is now `@Transactional(propagation = MANDATORY)` so any
  future caller outside a transaction fails fast instead of silently reintroducing the race.

Why lock the host and not the event. The storage cap is per host across all of their
events, so per-event locking would still race on storage. Contention is per host and lasts
a few milliseconds. See ADR-014 for alternatives considered.

### C4: Validate membership on upload

Problem. `requestUploadUrl` stored `request.membershipId()` without checking it, so a client
could attach any UUID (including another event's membership) and choose any uploader name.

Change. If `membershipId` is sent, it must resolve via
`findByIdAndEventIdAndStatus(id, eventId, ACTIVE)`, otherwise 403 ("Your guest session for
this event is no longer valid. Please rejoin the event."). The stored uploader name is the
membership's `guestDisplayName` when present. Requests without a membership still work as
before.

### C5: Replica-safe processing claim

Problem. `MediaProcessingScheduler` selected UPLOADED rows with a plain query and relied on
running a single API instance. Two replicas would thumbnail the same media twice.

Change.

- New `media/processing/MediaWorkQueue.claim(limit, staleCutoff)` runs one statement:
  `UPDATE media SET status='PROCESSING', updated_at=now() WHERE id IN (SELECT id ... ORDER BY
  created_at, id LIMIT :limit FOR UPDATE SKIP LOCKED) RETURNING id`. It uses
  `NamedParameterJdbcTemplate` to bypass the JPA first-level cache.
- `MediaProcessingScheduler` now calls `workQueue.claim` and processes the returned ids.
- `MediaRepository.findProcessableBatch` was removed (no other callers).

Operational note. `MEDIA_PROCESSING_STALE_SECONDS` (default 300) must exceed the slowest
processing run, or a long video can be reclaimed by another poller while still running.

### C6: Documentation brought in line with the code

- `docs/ARCHITECTURE.md` rewritten: in-process processing, the new upload flow with size
  rejection, guest credential model, concurrency and quotas, known gaps, and the deferred
  broker design as an appendix.
- `docs/DECISIONS.md`: ADR-005 marked superseded; added ADR-013 (in-process processing),
  ADR-014 (row locks), ADR-015 (rate limiter stays in-process for now), ADR-016 (membership
  id as guest credential).
- `docs/API.md`: upload size rule, membership validation, `upload_rejected`,
  `X-Membership-Id`, `ownedByRequester`, and the delete-own-media endpoint.
- `README.md`: roadmap now reflects shipped features; points to `docs/changes/`.

### C7: Deployment docs index

`docs/README.md` lists every doc with its status and names `DEPLOYMENT_RUNBOOK.md` as the
canonical deployment procedure. No deployment doc was deleted.

### C8: Repository hygiene

- `.gitattributes` normalises text to LF in the repository and keeps CRLF for `.cmd`,
  `.bat`, `.ps1`. This removes the six whole-file "modifications" that were only CRLF
  conversions. To apply it to existing files once: `git add --renormalize .` then review
  `git status` before committing.
- `.gitignore` now ignores `*.tsbuildinfo`.
- The review's concern about `.idea/`, `.github/modernize/` and `production.env` was checked:
  they are already ignored and not tracked, so no change was needed. `.env` files with real
  secrets are not tracked.

## Files changed

Backend (main):

- `common/error/UploadRejectedException.java` (new)
- `media/processing/MediaWorkQueue.java` (new)
- `media/MediaService.java`
- `media/MediaController.java`
- `media/MediaRepository.java`
- `media/dto/MediaResponse.java`
- `media/dto/DeleteOwnMediaRequest.java`
- `media/processing/MediaProcessingScheduler.java`
- `media/r2/R2StorageService.java`
- `subscription/PlanLimitService.java`
- `user/UserRepository.java`
- `config/SecurityConfig.java`

Backend (test):

- `media/MediaServiceTest.java` (updated for new constructor and signature; 9 new tests)
- `media/processing/ConcurrencyControlIT.java` (new, Testcontainers)

Frontend and mobile: `frontend/src/lib/api.ts`, `frontend/src/lib/types.ts`,
`frontend/src/components/Gallery.tsx`, `mobile/app/event/[code].tsx`.

Docs and repo: `docs/ARCHITECTURE.md`, `docs/DECISIONS.md`, `docs/API.md`, `docs/README.md`
(new), `docs/changes/` (new), `README.md`, `AGENTS.md` (new), `CLAUDE.md` (new, points to AGENTS.md), `.gitattributes` (new),
`.gitignore`.

## Tests added

Unit (`MediaServiceTest`, Mockito):

- `requestUploadUrlReservesPendingMediaAndPresigns` now also asserts the signed length (C1)
- `requestUploadUrlLocksHostBeforeCheckingPlanLimits`: order is lock, check, insert (C3)
- `requestUploadUrlDoesNotReserveWhenQuotaExceeded` (C3)
- `requestUploadUrlRejectsMembershipFromAnotherEventOrInactive` (C4)
- `requestUploadUrlTakesUploaderNameFromMembershipNotRequest` (C4)
- `completeUploadRejectsObjectLargerThanDeclared` (C1)
- `deleteOwnMediaRejectsDisplayNameOnlyRequests` (C2)
- `deleteOwnMediaRejectsMediaWithoutUploaderMembership` (C2)
- `deleteOwnMediaAllowsActiveUploaderMembership` (C2)
- `galleryFlagsOnlyItemsOwnedByRequester` (C2)

Integration (`ConcurrencyControlIT`, real PostgreSQL via Testcontainers):

- `claimMarksRowsProcessingAndDoesNotReturnThemTwice` (C5)
- `staleProcessingRowsAreReclaimed` (C5)
- `concurrentClaimersReceiveDisjointRows`: claimer A holds locks, claimer B gets only the
  rest (C5)
- `hostRowLockSerialisesSecondLocker`: a second `findByIdForUpdate` waits for the first
  transaction (C3)

## Compatibility

- `DELETE /api/events/code/{code}/media/{mediaId}` now returns 400 without `membershipId`
  and 403 if it does not match. Old web clients that relied on name matching lose the
  ability to delete; they never should have had it.
- `R2StorageService.presignUpload` gained a third parameter. All callers are updated.
- `MediaService` constructor gained `UserRepository` and `PlanLimitService` (Spring injects
  them; tests updated).
- Clients must PUT exactly `sizeBytes` bytes. The web client already did (`file.size`); the
  mobile client was fixed.
- R2 bucket CORS needs no change: browsers set `Content-Length` themselves and it is not a
  preflight header.
- No database migration, no new environment variables.

## How to verify

```bash
cd backend && mvn verify          # unit + Testcontainers ITs (needs Docker)
cd frontend && npm run typecheck   # passes at hand-off
```

Manual checks after deploy:

1. Upload a photo as a guest, then open it: Delete is shown only on your own uploads.
2. In a second browser, join with the same display name: Delete must not appear on the
   first guest's photos, and a crafted DELETE request with only the name returns 403.
3. Request an upload URL for a 1 KB file and PUT a larger file to it: R2 returns 403.
4. Watch `eventshare_media_upload_rejected_total` in Prometheus; it should stay at 0 in
   normal use.

## Rollback

Revert this change set. There is no schema migration, so a code revert is complete. Media
rows marked FAILED/DELETED by C1 rejections stay as they are, which is correct.

## Follow-ups not done here

- Sweep PENDING rows older than the upload URL TTL plus a margin (they count toward quota).
- Shared rate-limit store before running a second API replica (ADR-015).
- More tests for billing, Stripe webhooks and the frontend.
