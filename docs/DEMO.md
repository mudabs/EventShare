# Interview demo mode

Demo mode gives EventShare ready-made logins and a realistic showcase, so you can open the
app in an interview and show every feature in a few minutes. It can run on your laptop
(local demo) and on the live site, and it resets itself every night.

## What gets seeded

| Item | Details |
|---|---|
| Demo host account | Username `demo-host` (`DEMO_HOST_USERNAME`) and the password from `DEMO_HOST_PASSWORD`. No email needed. Unlimited plan (whitelisted). Can manage only the two seeded demo events and cannot create additional events |
| Demo admin account | Optional (`DEMO_ADMIN_ENABLED`), username `demo-admin`. Platform admin, so you can show the admin panel |
| "Amara & Kofi's Wedding" | Active wedding, invite code `EVENTSHARE`. 8 guests, 18 generated photos spread over the last two days, 1 exact duplicate (flagged), 2 hidden photos in the moderation view, 46 visitors over two weeks for the analytics charts |
| "Product Team Offsite" | Archived conference event (code `TEAMDAY26X`) so the dashboard lists more than one event |
| Promo code `INTERVIEW30` | Grants Wedding Pro for 30 days; shows the promo redemption flow. Not shown on the public landing page; visible to admins in the Demo tab. Every reset removes any promo redemptions made with the demo accounts (any code), gives those redemptions back to the code, and removes the resulting plans |

The photos are drawn by the server (gradient landscapes, no third-party images, nothing to
license). They are uploaded to R2 and marked UPLOADED, so the normal processing pipeline
creates their thumbnails within a few seconds, exactly as for a real guest upload.

Every reset (nightly at 04:00 America/Chicago, at first startup, or from the admin panel)
deletes all events owned by the demo accounts and seeds them again. Anything an interviewer
uploads or changes is gone the next morning. Real users' events are never touched.

The public wedding event is deliberately bounded. By default it accepts 8 new upload
reservations per reset, at most 4 from any one visitor (client IP), and each file is limited
to 25 MiB; these limits are enforced in the
upload-reservation API, so hiding the upload control is not the security boundary. Set
`DEMO_GUEST_UPLOADS_ENABLED=false` for a completely read-only public demo. Reservations
remain counted if a guest later deletes the media, preventing repeated upload/delete cycles
from bypassing the cap.

The shared demo login is also restricted on the seeded events themselves: it cannot delete
them, rename them, change their cover photo, permanently delete photos, or open Stripe
checkout or the billing portal. Hiding and restoring photos and the display toggles still
work for the walkthrough, and the nightly reset restores them.

## Credentials: where they come from

No password is stored in the code or in git.

- `scripts/demo-up.ps1` / `scripts/demo-up.sh` copy `.env.demo.example` to `.env.demo`
  (git-ignored) and fill every `GENERATED` value with a random secret.
- At startup and at every reset, the API creates the demo users in Clerk (through the Clerk
  Backend API) or resets their passwords to the values in the environment. The username
  and password only change when you change them, so they are safe to put in a README.
  Changing `DEMO_HOST_PASSWORD` and restarting the API is enough to rotate it.
- The demo accounts sign in with **username and password**; they have no email by default.
  Locally, the API stores them with an address like `demo-host@demo.invalid`, which is
  never sent to Clerk and can never receive mail (`.invalid` is a reserved domain). It
  exists only because the unlimited-plan whitelist is keyed by email. Set
  `DEMO_HOST_EMAIL` only if your Clerk instance insists on an email.
- Password resets also sign the demo accounts out of other sessions, so each interview
  starts clean.

## Local demo (one command)

Prerequisites: Docker Desktop, a Clerk **development** instance and a separate R2 bucket.

1. **Clerk.** In the Clerk dashboard open your development instance. Under
   *User & authentication*, turn on **Username** and **Password**, and set **Email
   address** to optional (not required). Copy the publishable key, the secret key, and the
   Frontend API URL (`https://<name>.clerk.accounts.dev`).
2. **R2.** Create a bucket such as `eventshare-demo` and an API token for it. Add a CORS
   rule allowing `GET` and `PUT` from `http://localhost:8090` with header `Content-Type`.
3. **First run** from the repository root:

   ```powershell
   .\demo.cmd                 # Windows (or: .\scripts\demo-up.ps1)
   ```

   ```bash
   scripts/demo-up.sh         # macOS / Linux
   ```

   This creates `.env.demo` and lists the keys still to fill in.
4. Paste the Clerk and R2 values into `.env.demo` (the lines that say `PASTE`). Use the
   Frontend API URL for `CLERK_ISSUER`, and the same URL plus
   `/.well-known/jwks.json` for `CLERK_JWKS_URL`.
5. **Run it again.** The stack starts on <http://localhost:8090> (the dev stack stays on
   8088) and the script prints the guest link and both logins. The first start builds the
   images and seeds the photos; allow one to three minutes.

On the sign-in page, type the username (for example `demo-host`) in the "Email address or
username" field, then the password.

Useful commands:

```bash
docker compose -p eventshare-demo logs -f api    # watch seeding ("Demo reset (startup) finished")
scripts/demo-up.sh --down                        # stop (data kept)     Windows: .\demo.cmd -Down
scripts/demo-up.sh --wipe                        # stop and delete DB   Windows: .\demo.cmd -Wipe
```

## Live site (vps01)

Add the demo variables to the server's `.env` (see the demo block in `.env.example`) and
redeploy. Differences from the local demo:

- In your **production** Clerk instance, make the same settings as for the local one
  (Username and Password on, Email address optional), and turn off **Device Trust**
  (*Protect > Rules > Device Trust > Manage*, toggle *Enable* off, save). Device Trust asks
  for an email code when someone signs in with a password from a new device; recruiters
  using the demo login could not complete that. Turning it off weakens credential-stuffing
  protection for every account, which is the trade-off for a public demo login.
- Pick a fixed password, put `DEMO_HOST_USERNAME` and `DEMO_HOST_PASSWORD` in the server's
  `.env`, and the same pair in your README. They stay valid until you change them.
- Set `DEMO_GUEST_UPLOADS_ENABLED=true`, `DEMO_MAX_GUEST_UPLOADS=8`,
  `DEMO_MAX_GUEST_UPLOADS_PER_IP=4` and `DEMO_MAX_GUEST_UPLOAD_BYTES=26214400` (or disable
  uploads completely). These reach the API only because they are listed in
  `docker-compose.yml`; a new `DEMO_*` setting must be added there too. The demo host is
  whitelisted for billing-plan display but is still blocked from creating arbitrary events.
- Keep `DEMO_ADMIN_ENABLED=false`. The admin panel shows every real account and can disable
  users; an interviewer must not get that on the live site. Show the admin panel from the
  local demo instead.
- Decide whether to set `DEMO_SHOW_CREDENTIALS=true`. It prints the host login on the public
  landing page. That is convenient for recruiters, but anyone can then sign in as the demo
  host (which only has access to demo events, and resets nightly).
- Demo photos go into the live bucket under their own event prefixes, and a reset deletes
  only those objects.

## Suggested interview walkthrough (about 8 minutes)

| Time | Show | Point to make |
|---|---|---|
| 0:00 | Landing page, "Try it without signing up" | Product in one sentence: a shared event album, guests need no account |
| 0:45 | Open the guest gallery (on the live site, scan the QR code with your phone; on the local demo, use a second browser window, since a phone cannot reach your laptop's localhost) and upload a photo | Upload goes **directly to R2** through a presigned URL; the API never handles the bytes. The URL signs the exact size |
| 2:00 | Watch the thumbnail appear | In-process processing; rows are claimed with `FOR UPDATE SKIP LOCKED`, so it scales to several replicas |
| 3:00 | Sign in as the host, open the wedding's manage page | Moderation (2 hidden photos), duplicate detection by SHA-256, guest list |
| 4:30 | Owner dashboard and analytics | Live aggregation queries; keyset pagination for the gallery |
| 5:30 | Pricing page, then redeem promo code `INTERVIEW30` | Plan limits enforced in the same transaction as the insert, under a row lock (race-safe quotas) |
| 6:30 | Admin panel (local demo), Demo tab, "Reset demo now" | Operational thinking: idempotent seeding, advisory lock, R2 cleanup after commit |
| 7:30 | Architecture diagram from `docs/ARCHITECTURE.md` | Modular monolith on one VPS, CI/CD to a self-hosted runner, ADRs for every decision |

## How it works (for maintainers)

| Piece | File |
|---|---|
| Settings (`eventshare.demo.*`, env `DEMO_*`) | `backend/.../demo/DemoProperties.java`, `application.yml` |
| Clerk account create / password reset | `ClerkUserClient.ensureUserWithPassword` |
| Seed and reset logic | `backend/.../demo/DemoSeeder.java` |
| Startup seeding and nightly cron | `backend/.../demo/DemoLifecycle.java` |
| `GET /api/demo/info` (public), `POST /api/admin/demo/reset` (admin) | `backend/.../demo/DemoController.java` |
| Generated photos | `backend/.../demo/DemoImageGenerator.java` |
| Landing panel and admin tab | `frontend/src/components/DemoBanner.tsx`, `AdminDemo.tsx` |
| Local stack | `.env.demo.example`, `scripts/demo-up.*`, `demo.cmd` |
| Integration test | `backend/src/test/.../demo/DemoSeederIT.java` |

A reset runs in three steps. First it uploads the new photos to R2. Then, in one database
transaction holding a PostgreSQL advisory lock, it deletes every event owned by the demo
accounts (cascading to media, memberships and visits), deletes the demo promo code, and
inserts the new showcase. Finally, after the commit, it deletes the previous demo's R2
objects. If the transaction fails, the freshly uploaded objects are deleted instead, and the
previous demo stays as it was.
