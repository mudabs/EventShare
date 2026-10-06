# Architecture Decision Records

Each record states the context, the decision, and the consequences (including tradeoffs).

## ADR-001: Modular monolith plus worker, not microservices

Context. A single developer must deploy and operate the system on one VPS, while the brief
asks for distributed-system principles. Media processing has different CPU, memory, and
failure characteristics from request handling.

Decision. Build one API deployable containing all synchronous domains (user, event, media,
audit) with clean package boundaries, and a separate worker for asynchronous processing.
Communicate via RabbitMQ and share state via PostgreSQL and R2.

Consequences. Simple transactions and local development on the request path; independent
scaling and isolation for processing. The boundaries are clean enough to extract a module
into its own service later. The cost is that the API is one process, so a severe API bug can
affect all request domains at once; this is acceptable at the target scale.

## ADR-002: Clerk-centric authentication, API as resource server

Context. The brief specifies Clerk for auth and also lists JWT validation on the backend.
Guests must join with minimal friction.

Decision. Clerk is the identity provider and issues JWTs to the frontend. The API is a
stateless OAuth2 resource server that verifies the JWKS signature and (when configured) the
issuer and audience. Local users are provisioned just in time from the token subject.

Consequences. No password or session handling in our code, and the frontend gets a polished
auth UX. The dependency on Clerk is a vendor coupling, mitigated by the fact that only token
verification and a thin claim mapping touch Clerk specifics. Roles are coarse at the filter
level; fine-grained authorization is ownership-based in services.

## ADR-003: Capability-based guest access via invite code

Context. Requiring guests to sign up would defeat the product. But public write endpoints
must not be abusable.

Decision. Possession of a high-entropy, non-enumerable invite code authorizes a guest to
view that event's gallery and to request presigned upload URLs for it. Guest endpoints are
public at the HTTP layer; authorization, rate limiting, and content validation happen in the
service layer. Guests optionally provide a display name, stored on a lightweight membership.

Consequences. Near-zero friction for guests. Risk is bounded: codes are unguessable, uploads
are rate limited per IP and validated for type and size, presigned URLs are short lived and
scoped to one object key, and hosts can disable auto-approval to review uploads. If a code
leaks, a host can archive the event. A future enhancement is per-guest revocable tokens.

## ADR-004: Presigned direct-to-R2 uploads and downloads

Context. Photos and videos are large. Routing them through the API would waste bandwidth,
memory, and request threads.

Decision. The API issues presigned PUT URLs; the browser uploads bytes straight to R2. The
API only records metadata and, on completion, performs a HEAD to confirm the object and read
its authoritative size. Gallery and downloads use presigned GET URLs.

Consequences. The API and worker are never in the inbound media data path, which keeps them
small and cheap. Clients need correct content-type handling, and presigned URLs must be short
lived. The completion step makes the flow two-phase, which the idempotent complete endpoint
handles cleanly.

## ADR-005: Asynchronous processing over RabbitMQ with a dead-letter queue

Status. Superseded by ADR-013 (processing now runs in-process). Kept for history.

Context. Thumbnailing and metadata extraction are slow and occasionally fail (unsupported
formats, transient storage errors). They must not block the upload acknowledgement.

Decision. On completion the API publishes a typed event to a topic exchange. The worker
consumes it. Work queues dead-letter to a fanout DLX and durable DLQ; the listener retries
with backoff before dead-lettering.

Consequences. Uploads acknowledge instantly and processing happens out of band. Failures are
visible (media row marked FAILED) and preserved (DLQ) rather than lost. The exchange, queue,
and routing-key names are a contract duplicated in both services; a shared contracts module
is a noted future refactor.

## ADR-006: Keyset pagination for the gallery

Context. Galleries grow large and receive concurrent inserts. Offset pagination degrades and
can skip or repeat rows under concurrent writes.

Decision. Paginate by a composite keyset cursor of `(created_at, id)` descending, backed by
the index `(event_id, moderation_state, created_at desc)`. The cursor is an opaque base64
token.

Consequences. Stable, index-friendly pagination that performs the same on page 1 and page
1000 and does not duplicate rows when new media arrives. The cost is that arbitrary
random-access paging (jump to page N) is not supported, which the infinite-scroll UX does not
need.

## ADR-007: Exact SHA-256 duplicate detection first

Context. Guests often upload the same shared photo multiple times. The brief asks for exact
detection now and AI clustering later.

Decision. The client computes a SHA-256, sent at completion. The API flags a media row as a
duplicate of the earliest row in the same event with the same hash, storing the relationship
for host review rather than blocking the upload.

Consequences. Cheap, exact, and immediately useful. It does not catch re-encoded or resized
near-duplicates; the schema's duplicate columns and a clean detection seam allow a perceptual
or AI approach to be added without migration churn.

## ADR-008: Polling baseline for live gallery, WebSocket later

Context. The brief asks for real-time gallery updates. A robust slice should not depend on a
half-wired socket layer.

Decision. Ship a working near-real-time experience by re-polling the gallery's first page on
an interval through React Query, and treat WebSocket push as the next iteration.

Consequences. Simple and reliable at event scale, with a small latency and a little redundant
polling. WebSocket or server-sent events will reduce latency and load for large live events
and is the documented upgrade path.

## ADR-009: Per-user subscriptions (not per-event purchases)

Context. The pricing is quoted per event, but a single subscription per account is simpler to
operate and reason about. Decision. One active subscription per user governs all their events; the
four tiers are recurring (Basic, Wedding Pro) or one-time (Lifetime) per account, with per-event
numeric limits and per-account event count and storage. Consequences. Simple billing and limit
resolution. If true per-event purchases are needed later, the subscriptions table and checkout flow
would change to a per-event purchase record.

## ADR-010: Stripe via REST, not the SDK

Context. The stripe-java SDK moves fast and a wrong method fails the entire backend build, which we
cannot validate without live keys. Decision. Call the Stripe REST API directly with the JDK HTTP
client and verify webhook signatures with HMAC-SHA256. Consequences. Zero SDK-version coupling and
full control; slightly more code, and new Stripe features must be wired by hand.

## ADR-011: Admin role from the database, seeded by email

Context. Clerk tokens do not carry our platform role, and we need a deterministic first admin.
Decision. The admin role lives on the user row; at just-in-time provisioning, any email listed in
ADMIN_EMAILS is granted ADMIN. Admin endpoints check the persisted role rather than a JWT claim.
Consequences. Self-contained and not dependent on Clerk configuration. Promoting or demoting admins
is a database or config change.

## ADR-012: Whitelist and live quota enforcement

Context. VIP and internal accounts need unlimited access, and limits must be enforced cheaply.
Decision. A whitelist table grants an effective unlimited plan, resolved in PlanLimitService.
Quotas (events, per-event uploads, account storage) are checked with live aggregation queries at
the point of action. Consequences. Correct and simple at current scale. If aggregation becomes hot,
the event_analytics and storage_usage tables are already in place to switch to maintained rollups.

## ADR-013: In-process media processing with the media table as the queue

Status. Accepted (recorded 2026-10-05; the code change predates this record).
Context. On one small VPS the RabbitMQ broker and separate worker doubled the moving parts
without adding capacity. Decision. The API runs a `@Scheduled` poller; `media.status`
(UPLOADED, PROCESSING, PROCESSED, FAILED) is the queue. Consequences. One fewer service to
deploy and monitor. Processing shares CPU with request handling, so batch size stays small.
The worker and broker config are retained in the repo for later reintroduction.

## ADR-014: Row locks for quota checks and work claiming

Status. Accepted 2026-10-05. Context. Two check-then-act races existed: parallel upload
requests could all pass the plan-limit count before any inserted, and two API replicas could
claim the same media row for processing. Decision. (a) Lock the host `users` row with
`SELECT ... FOR UPDATE` and run the plan check and the PENDING insert in the same
transaction. (b) Claim processing work with one `UPDATE ... WHERE id IN (SELECT ... FOR
UPDATE SKIP LOCKED) RETURNING id` statement. Consequences. Upload-URL requests for one host
are serialised for a few milliseconds each, which is negligible at event scale. Locking per
host (not per event) is required because the storage cap spans all of a host's events.
Alternatives considered: a counter table with conditional `UPDATE ... WHERE used < limit`
(faster under very high contention, but needs backfill and reconciliation), and
SERIALIZABLE isolation (correct but turns contention into retries the client must handle).

## ADR-015: Rate limiter stays in-process for now

Status. Accepted 2026-10-05. Context. `RateLimiter` keeps fixed-window counters in memory,
so limits are per replica. Decision. Keep it while production runs one API replica; adopt
Redis (or Bucket4j with a shared store) as part of the change that adds a second replica.
Consequences. No new infrastructure today. The limitation is documented in ARCHITECTURE.md
so it is not forgotten when scaling.

## ADR-016: Guest membership id is the guest's credential

Status. Accepted 2026-10-05. Context. Guest self-delete accepted either the uploader's
membership id or a matching display name. Names are shown in the gallery, so anyone could
delete anyone's media. Decision. Only an ACTIVE membership id matching the media's
`uploader_membership_id` authorises self-delete. Upload requests that carry a membership id
must reference an ACTIVE membership of the same event, and the uploader name is copied from
the membership. The gallery reports ownership via `ownedByRequester` computed from an
`X-Membership-Id` header, so the id is never exposed to other guests. Consequences. Media
uploaded before joining (no membership) can only be removed by the host. Membership ids must
be treated as secrets by clients (they live in the guest's local storage).

## ADR-017: Demo mode seeds real Clerk accounts and resets only demo-owned data

Status. Accepted 2026-10-05. Context. The app doubles as an interview demo and needs logins
that always work and identical showcase data every time, locally and on the live site.
Decision. A `demo` module, off by default, creates or re-passwords demo users through the
Clerk Backend API (no authentication bypass), seeds two events with server-generated photos
that go through the normal processing pipeline, and resets nightly. A reset deletes only
events hosted by demo accounts, under a PostgreSQL advisory lock, uploading new objects
before the transaction and deleting old ones after commit. Passwords come only from the
environment. Consequences. The demo shows the real sign-in flow and real pipeline. It
depends on Clerk and R2 being configured. The demo admin and public credential display are
opt-in because the admin panel exposes real accounts on a live site. Alternatives
considered: a Flyway seed migration (cannot create Clerk users or R2 objects, and would run
in production), and an auth-bypass "demo login" (a standing back door).

## ADR-018: ZIP downloads are built in the browser, gated by the host's plan

Status. Accepted 2026-10-05. Context. Paid plans promise ZIP downloads of an event. The API
runs on a home server behind a tunnel with limited upload bandwidth, while R2 serves files
quickly and with free egress. Decision. The browser fetches originals directly from R2 and
streams them into a ZIP with `client-zip` (store-only, since photos and videos are already
compressed). On Chrome and Edge the ZIP streams straight to disk through the File System
Access API; other browsers assemble it in memory, capped at 1.5 GB with a clear message.
The public event summary exposes `zipDownloads` from the host's plan; the free plan gets
per-file downloads only. Single-file downloads use a presigned URL with
`response-content-disposition: attachment`, because browsers ignore `<a download>` on
cross-origin URLs. Consequences. No server CPU or bandwidth spent on ZIPs. Requires the R2
bucket CORS rule to allow GET from the app origin (already in place). Plan gating is a
product boundary in the UI, not a security control, since guests can always save files one
by one. Alternative considered: a server-side streaming ZIP endpoint, which works on every
browser but pushes every byte through the home server's uplink.
