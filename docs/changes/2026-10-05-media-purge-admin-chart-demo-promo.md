# 2026-10-05: Automatic media purge, admin growth chart, demo promo reset

| Field | Value |
|---|---|
| Date | 2026-10-05 |
| Author | Munashe Mudabura, with Claude (AI pair programmer) |
| Base | `be05bde` plus the uncommitted chart fix and privacy/terms/reporting change |
| Schema migration | `V13__media_purge.sql` |
| New configuration | `MEDIA_PURGE_DAYS` (default 30) |
| Build status at hand-off | Frontend `tsc --noEmit` passes; admin chart rendered and checked in headless Chromium. Java files parse; not compiled or tested here (Maven Central blocked). Run `mvn verify` |

## MP1: Deleted media is erased automatically after 30 days

Problem. Deleting a photo (guest self-delete, host "Delete", oversize rejection) only set
`moderation_state = DELETED`; the row and the R2 objects stayed forever. Deleting an event
only set `events.deleted_at`. The Privacy Policy had to promise manual erasure.

Change.

- `Media.setModerationState` (explicit setter, replacing Lombok's) records `deleted_at`
  when an item becomes DELETED and clears it on restore, so every delete path is covered.
- `V13` backfills `deleted_at = updated_at` for items already DELETED and adds partial
  indexes for the purge queries.
- `privacy/MediaPurgeJob` runs daily (03:45): erases DELETED items whose `deleted_at` is
  older than `MEDIA_PURGE_DAYS`, then events soft-deleted longer ago than that, with all
  their media. R2 objects (original and thumbnail) are deleted first; the row is deleted only
  if that succeeded, so a storage outage is retried the next day instead of orphaning files.
  Batches of 200; idempotent across replicas.
- `/privacy` now states: deleted items are erased permanently 30 days later (restorable by
  the host until then); deleted events likewise.

Tests: `MediaPurgeJobIT` (Testcontainers, R2 mocked): delete sets and restore clears
`deleted_at`; only items deleted more than 30 days ago are purged (recent, visible and hidden
items kept); a failed R2 delete keeps the row; events deleted 45 days ago are purged with
their media; an event deleted 3 days ago is kept.

## MP2: Admin "new users" chart shows real months and the numbers

Problem. Labels were the month number ("05", "06" ...) and bars carried no values, so a
quiet period looked like an empty chart.

Change. New shared `components/BarChart.tsx` (separate bar and label rows, value above each
bar, optional "0" over empty periods, accessible summary). `AdminAnalytics` uses it with
month names built in UTC ("May 2026", "Jun", ... with the year on the first bar and every
January), shows "0" for empty months, and a header "N new in 6 months · T total".
`OwnerDashboard` now uses the same component (no visual change from the earlier fix).

## MP3: Demo promo code

- The promo code is no longer on the public landing page (`DemoBanner`) nor in the public
  `GET /api/demo/info`. Admins see it in the Demo tab through the new
  `GET /api/admin/demo/info`.
- Every demo reset now gives back promo redemptions made with the demo accounts, for any
  code: decrements `promo_codes.redemptions_used`, deletes the demo accounts'
  `promo_code_usage` rows (so the code can be redeemed again), then deletes their
  subscriptions. The demo code itself is recreated fresh as before.
- To clear the promo currently active on the live demo account: deploy, then Admin > Demo >
  "Reset demo now" (or wait for the nightly reset).

Test: `DemoSeederIT.resetGivesBackPromoRedemptionsMadeByDemoAccounts`.

## Files

Backend: `media/Media.java`, `privacy/MediaPurgeJob.java` (new), `demo/DemoSeeder.java`,
`demo/DemoController.java`, `application.yml`, `db/migration/V13__media_purge.sql` (new).
Tests: `privacy/MediaPurgeJobIT.java` (new), `demo/DemoSeederIT.java`.
Frontend: `components/BarChart.tsx` (new), `components/AdminAnalytics.tsx`,
`components/OwnerDashboard.tsx`, `components/DemoBanner.tsx`, `components/AdminDemo.tsx`,
`lib/api.ts`, `app/privacy/page.tsx`.
Ops and docs: `docker-compose.yml`, `.env.example`, `docs/API.md`, `docs/ENVIRONMENT.md`,
`docs/DEMO.md`, `docs/changes/2026-10-05-privacy-terms-and-reporting.md` (mapping row), this file.

## Verify

1. Delete a photo, then in the database: `SELECT deleted_at FROM media WHERE id = ...` is set;
   restore it and it is NULL again.
2. Optional quick check: set `MEDIA_PURGE_DAYS=1`, backdate a deleted item's `deleted_at`, and
   watch the next 03:45 run (log line "Purged N deleted media items").
3. Admin > Analytics shows month names with a number over every bar.
4. The landing page demo panel shows no promo code; Admin > Demo still shows it.
5. Redeem `INTERVIEW30` as `demo-host`, then "Reset demo now": the demo host is back to its
   whitelisted plan and the code can be redeemed again.
