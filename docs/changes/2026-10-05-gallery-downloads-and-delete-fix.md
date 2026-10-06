# 2026-10-05: Gallery downloads and delete fix

| Field | Value |
|---|---|
| Date | 2026-10-05 |
| Author | Munashe Mudabura, with Claude (AI pair programmer) |
| Base commit | `4502858` (Merge pull request #6) |
| Scope | Guest gallery (web), media API, public event summary, plan limits |
| Schema migration | None |
| New dependency | `client-zip` 2.5.1 (frontend, MIT, no transitive dependencies) |
| Build status at hand-off | Frontend `tsc --noEmit` passes; ZIP helper tested end to end in headless Chromium (3 files, duplicate names, progress, file name). `next build` and the Java build could not run here (Google Fonts and Maven Central blocked). CI covers both |

Change ids G1 to G5 are used in this write-up and in code comments where helpful.

## G1: Deleting your own photo reported "Delete failed"

Symptom. Toast: `Failed to execute 'json' on 'Response': Unexpected end of JSON input`. The
photo was in fact deleted (it was gone from the live gallery afterwards), but the UI showed
an error and did not refresh.

Cause. `DELETE /api/events/code/{code}/media/{id}` is a `void` controller method, so Spring
answered `200 OK` with an empty body. The web client's `request()` only skipped JSON parsing
for `204`, and `response.json()` throws on an empty body.

Fix. The endpoint now returns `204 No Content` (`@ResponseStatus`). `request()` in
`frontend/src/lib/api.ts` reads the body as text and parses JSON only when it is non-empty,
so any other void endpoint is safe too.

## G2: Viewer counter

The code already rendered `selectedIndex + 1`; on the live demo it showed "1 of 16" when
checked. To make the counter impossible to show 0, it now renders a derived `position`
(index + 1, or nothing if the open item is not in the list). The viewer also closes itself
if the open item leaves the gallery (deleted or hidden by the host), which previously left
a stale photo with no valid position.

## G3: "Download" opened the photo on its own page

Cause. The button was an `<a href download>` pointing at R2. Browsers ignore the `download`
attribute on cross-origin URLs and simply navigate to the image.

Fix. Each media item now carries `downloadUrl`: a presigned GET with
`response-content-disposition: attachment; filename="..."; filename*=UTF-8''...`
(`R2StorageService.presignAttachment`). The browser saves the file directly. Used by the
viewer's Download button and by multi-file downloads.

## G4: Downloads follow the plan; ZIP for paid plans

Product rule from the owner: "Download all" and ZIP files are a paid-plan feature (Basic,
Wedding Pro, Lifetime, or any unlimited account). The free plan keeps per-file downloads.

- Backend: `PlanLimitService.hostHasZipExport(event)` reads the host's effective plan
  (`plans.zip_export`). `GET /api/events/code/{code}` now includes `zipDownloads`.
- Frontend: `Gallery` receives `zipDownloads`, `allowDownloads` (the host's existing "Allow
  guest downloads" setting, which the gallery previously ignored) and the event name.
- ZIPs are built in the browser (`frontend/src/lib/download.ts`, ADR-018): originals are
  fetched straight from R2 and streamed into a store-only ZIP. Chrome and Edge stream to disk
  via the save dialog; other browsers build it in memory, capped at 1.5 GB with a clear
  message. Duplicate names get " (2)" suffixes. The save dialog opens before any network
  work so the browser still treats it as a response to the click.

## G5: Less cluttered toolbar

Before: "Select images", "Batch download (n)", "Download all in event" always visible, plus
"Clear" and "Download selected" in selection mode.

After:

- One "Select" toggle.
- "Clear" and "Download (n)" (free) or "Download ZIP (n)" (paid) appear only once at least
  one item is selected.
- "Download all (ZIP)" appears only on paid plans, and only outside selection mode.
- No download controls at all when the host turned guest downloads off.
- "Batch download (visible)" was removed (it duplicated "Download all" with a confusing
  partial scope).

## Files

Backend: `media/MediaController.java` (204), `media/dto/MediaResponse.java` (`downloadUrl`),
`media/r2/R2StorageService.java` (`presignAttachment`), `media/MediaService.java`,
`media/MediaModerationService.java`, `subscription/PlanLimitService.java`
(`hostHasZipExport`), `event/dto/PublicEventResponse.java` (`zipDownloads`),
`event/EventService.java` (injects `PlanLimitService`).

Tests: `MediaServiceTest.galleryItemsCarryAttachmentDownloadUrl`,
`PlanLimitServiceZipTest` (free, paid, whitelisted), `EventServiceTest` constructor update.

Frontend: `lib/api.ts`, `lib/download.ts` (new), `lib/types.ts`, `components/Gallery.tsx`,
`app/e/[code]/page.tsx`, `package.json`, `package-lock.json`.

Docs: this file, `docs/changes/README.md`, `docs/API.md`, `docs/DECISIONS.md` (ADR-018),
`docs/README.md`.

## How to verify

1. As a guest who joined, upload a photo, open it, click Delete: success toast, the viewer
   closes, the photo disappears.
2. Open any photo: counter reads "1 of N" for the first item.
3. Click Download in the viewer: the file is saved, no new page opens.
4. Demo event (demo host is unlimited, so it behaves like a paid plan): "Download all (ZIP)"
   is visible; select 3 photos and "Download ZIP (3)" saves one ZIP.
5. An event whose host is on the free plan: no "Download all"; selecting photos shows
   "Download (n)" and saves the files individually (the browser may ask once to allow
   multiple downloads).
6. Host turns off "Allow guest downloads": no Select or Download controls for guests.

## Rollback

Revert the change set; no data or schema is involved.

## Follow-ups

- The owner's manage page (`OwnerGallery`) has no download controls; hosts may want
  "Download all" there too.
- Very large ZIPs on Safari and Firefox are limited by memory; a server-side streaming
  endpoint could serve those browsers if needed.
