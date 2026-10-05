# 2026-10-05: Interview demo mode (seeded credentials and showcase data)

| Field | Value |
|---|---|
| Date | 2026-10-05 |
| Author | Munashe Mudabura, with Claude (AI pair programmer) |
| Builds on | [Security and correctness hardening](2026-10-05-security-and-correctness-hardening.md) (same day, not yet committed at the time of writing) |
| Scope | New `demo` backend package, Clerk client, R2 service, compose, local demo scripts, landing page and admin UI, docs |
| Schema migration | None |
| Breaking changes | None. Everything is off unless `DEMO_ENABLED=true` |
| Build status at hand-off | Frontend `tsc --noEmit` passes. `demo-up.sh` tested end to end with a stubbed `docker`. `DemoProperties` and `DemoImageGenerator` compiled and run with JDK 21. The rest of the backend parses but was **not compiled or tested** (no Maven Central access in the authoring sandbox): run `cd backend && mvn verify`. `demo-up.ps1` was not executed (no PowerShell available) |

## Why

The project is used as an interview demo as well as a real product. An interviewer needs
to see a full event (photos, guests, moderation, analytics) within seconds, and the
presenter needs logins that always work and data that is the same every time. Before this
change that meant creating accounts and uploading photos by hand before each interview.

Requirements agreed with the owner: credentials for a local run with test keys; works both
locally and on the live site; full showcase content; nightly reset.

Change ids D1 to D8 below are used in this write-up only. In code, search for "demo" or
`docs/DEMO.md`.

## Summary

| Id | Change |
|---|---|
| D1 | `demo` package: `DemoProperties`, `DemoSeeder`, `DemoLifecycle`, `DemoController`, `DemoImageGenerator` |
| D2 | `ClerkUserClient.ensureUserWithPassword`: create the demo users in Clerk, or reset their passwords |
| D3 | `R2StorageService.uploadBytes` for generated photos |
| D4 | `GET /api/demo/info` (public) and `POST /api/admin/demo/reset` (admin); permitAll rule for the info endpoint |
| D5 | Local one-command demo: `.env.demo.example`, `scripts/demo-up.sh`, `scripts/demo-up.ps1`, `demo.cmd`; separate compose project and port (8090) |
| D6 | Frontend: `DemoBanner` on the landing page, `AdminDemo` tab with "Reset demo now" |
| D7 | Compose fix: `ADMIN_CLERK_USER_IDS` and `DEMO_EMAILS` were documented but never passed to the API container; now they are, plus all `DEMO_*` variables and `EVENTSHARE_HTTP_PORT` |
| D8 | Username logins: demo accounts sign in with `demo-host` / `demo-admin` and a password; email optional (addendum below) |

## Design decisions

**Credentials live in the environment, not in code or git.** Passwords have no defaults.
The scripts generate them into the git-ignored `.env.demo`; on the server they go in the
existing `.env`. `DemoProperties.toString()` masks them.

**Real Clerk accounts, not an auth bypass.** A "demo login" that skipped Clerk would leave
a back door in production code and would not show the real sign-in flow. Instead, the seeder
calls the Clerk Backend API to create the users or patch their password with
`skip_password_checks` and `sign_out_of_other_sessions`. Since D8 they sign in with a
username, so no email address is needed (see "Addendum: username logins" below).

**Generated photos instead of stock images.** `DemoImageGenerator` draws gradient
landscapes with Java2D and no text, so it needs no fonts on the slim JRE image, has no
licensing questions, and is deterministic (the intentional duplicate is the same bytes
twice). The photos go through the real processing pipeline (rows inserted as UPLOADED).

**Reset is scoped to demo-owned data.** It deletes events whose host is a demo account,
their audit rows, the demo accounts' subscriptions, and the demo promo code. It refuses to
run if a non-demo event already uses a demo invite code. Real users are never touched.

**Reset is safe under concurrency and failure.**

1. Photos are uploaded to R2 first, outside any transaction.
2. One transaction takes `pg_advisory_xact_lock`, deletes the old demo, and inserts the new
   one, so two replicas resetting at once run one after the other.
3. Old R2 objects are deleted only after commit. If the transaction fails, the new uploads
   are deleted instead and the old demo remains intact.

**Live-site guard rails.** `DEMO_ADMIN_ENABLED` defaults to false (the admin panel exposes
real accounts) and `DEMO_SHOW_CREDENTIALS` defaults to false. Both are true only in
`.env.demo.example`, where there are no real users.

See ADR-017 in `docs/DECISIONS.md`.

## Files

Backend (new): `demo/DemoProperties.java`, `demo/DemoSeeder.java`, `demo/DemoLifecycle.java`,
`demo/DemoController.java`, `demo/DemoImageGenerator.java`.

Backend (changed): `EventShareApplication.java` (registers `DemoProperties`),
`common/security/ClerkUserClient.java`, `media/r2/R2StorageService.java`,
`config/SecurityConfig.java`, `resources/application.yml` (`eventshare.demo.*`).

Tests (new): `demo/DemoPropertiesTest.java` (unit), `demo/DemoSeederIT.java`
(Testcontainers, Clerk and R2 mocked with `@MockitoBean`).

Frontend: `components/DemoBanner.tsx` (new), `components/AdminDemo.tsx` (new),
`app/page.tsx`, `app/admin/page.tsx`, `lib/api.ts`, `lib/types.ts`.

Ops: `.env.demo.example` (new), `scripts/demo-up.sh` (new), `scripts/demo-up.ps1` (new),
`demo.cmd` (new), `docker-compose.yml`, `.env.example`, `.gitignore` (`.env.demo`).

Docs: `docs/DEMO.md` (new: setup, live-site notes, an 8-minute interview walkthrough),
`docs/API.md`, `docs/ENVIRONMENT.md`, `docs/DECISIONS.md` (ADR-017), `docs/README.md`,
`README.md`, `AGENTS.md`, this file.

## Tests

`DemoPropertiesTest`: defaults when settings are blank, passwords never defaulted, invite
codes upper-cased, photo count capped at 60, generated JPEGs valid, deterministic, and in
both orientations.

`DemoSeederIT` against real PostgreSQL:

- `resetSeedsTheShowcase`: 2 demo events; main event has the configured photo count, 1
  duplicate, 2 hidden, all UPLOADED, 8 guests, 46 visits; promo and whitelist exist;
  creation time backdated; admin has ADMIN role; every photo uploaded to R2.
- `secondResetReplacesDataAndDeletesOldObjects`: new event ids, still exactly 2 events, 1
  promo, 1 whitelist row; all previous R2 objects deleted.
- `resetNeverTouchesRealUsersData`: a real user's event survives two resets.
- `inviteCodeOwnedByRealUserAbortsAndCleansUpUploads`: clear error naming
  `DEMO_INVITE_CODE`, uploaded photos removed, nothing committed.

## How to verify

```bash
cd backend && mvn verify            # includes DemoSeederIT (needs Docker)
cd frontend && npm run typecheck
.\demo.cmd   or   scripts/demo-up.sh   # see docs/DEMO.md for the Clerk and R2 setup
```

Then: open <http://localhost:8090>; the landing page shows "Live demo" with both logins.
The guest link shows the gallery, and thumbnails appear within a few seconds. Sign in as
host and check the two events, the moderation view and the analytics. Sign in as admin,
open the Demo tab and click "Reset demo now": new photos appear and old ones are gone.

## Rollback

Set `DEMO_ENABLED=false` (instant, no deploy needed beyond a restart), or revert the change.
Demo rows already seeded can be removed by enabling once and deleting the demo accounts'
events from the admin panel, or left in place.

## Known limitations and follow-ups

- On a production Clerk instance, Device Trust must be turned off for a public demo login
  (it would ask for an email code on a new device). This lowers credential-stuffing
  protection for all accounts.
- Restarting the API re-applies demo passwords, which signs out current demo sessions.
- The local demo needs a Clerk development instance and an R2 bucket; a MinIO profile could
  remove the R2 dependency later.
- `demo-up.ps1` should be run once on Windows to confirm it behaves like the tested bash
  script.

## Addendum: username logins (D8)

Added the same day at the owner's request: the owner did not want to create a real email
account for the demo, and the login must be fixed so it can go in the README.

What changed:

- `DemoProperties`: new `hostUsername` / `adminUsername` (`DEMO_HOST_USERNAME`,
  `DEMO_ADMIN_USERNAME`, defaults `demo-host` / `demo-admin`). `hostEmail` / `adminEmail`
  no longer have defaults and are optional. New `hostLocalEmail()` / `adminLocalEmail()`
  return the configured email or `<username>@demo.invalid`.
- `ClerkUserClient.ensureUserWithPassword(username, email, password, first, last)`: looks
  the user up by `username` (or by email when no username is set), creates it with
  `username` and, only if configured, `email_address`.
- `DemoSeeder`: passes the username to Clerk; stores `hostLocalEmail()` on the local user
  row and whitelists that address, because the unlimited-plan whitelist is keyed by email.
- `DemoController` / `DemoBanner`: logins are `{ role, username, password }`.
- `.env.demo.example`, `.env.example`, `docker-compose.yml`, `application.yml`, scripts and
  docs updated; the demo admin no longer needs an `ADMIN_EMAILS` entry (the seeder sets
  the role).
- Tests: `DemoPropertiesTest` checks the username defaults and the `.invalid` local
  address; `DemoSeederIT` seeds username-only accounts and asserts the whitelist row.

Why `.invalid`: RFC 2606 reserves it, so the address can never receive mail and can never
belong to someone else. A made-up address on a real domain could be claimed by whoever owns
that domain and used to reset the demo password.

Clerk settings required (both instances): Username on, Password on, Email address optional;
production also needs Device Trust off for a public login. If an instance still requires an
email, set `DEMO_HOST_EMAIL` to an address on a domain you own; it is never shown to
interviewers.

Build status: frontend type-check passes; `DemoProperties` compiled and run with JDK 21;
other Java files parse but were not compiled (same Maven limitation as above).
