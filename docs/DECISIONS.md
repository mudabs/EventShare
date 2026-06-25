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
