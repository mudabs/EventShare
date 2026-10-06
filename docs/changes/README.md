# Change log (write-ups)

Each significant change gets one dated file here: what was wrong, what changed, which files,
how it was tested, compatibility notes, and how to roll back. Newest first. Change ids
(C1, C2, ...) are also written into code comments and tests, so
`grep -rnw "C3" backend/src frontend/src mobile` finds every location a change touched.

| Date | Write-up | Summary |
|---|---|---|
| 2026-10-05 | [Gallery downloads and delete fix](2026-10-05-gallery-downloads-and-delete-fix.md) | Guest delete no longer reports a false failure; real file downloads; ZIP downloads for paid plans; selection-only download button; viewer counter hardening |
| 2026-10-05 | [Interview demo mode](2026-10-05-interview-demo-mode.md) | Seeded demo logins via Clerk, showcase events with generated photos, nightly reset, one-command local demo |
| 2026-10-05 | [Security and correctness hardening](2026-10-05-security-and-correctness-hardening.md) | Upload size enforcement, guest delete authorisation, race-safe quotas, membership validation, replica-safe processing, docs refresh |

## Template for new entries

Copy the 2026-10-05 file's headings: header table (date, author, base commit, scope,
migration, breaking changes, build status), Why, Summary table, Details per change id,
Files changed, Tests added, Compatibility, How to verify, Rollback, Follow-ups.
