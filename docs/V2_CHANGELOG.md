# EventShare V2 Implementation Changelog

V2 turned the MVP into a multi-tenant SaaS. Built in nine phases on top of the V1 slice.

Phase 0 (prerequisites). Java 25 toolchain (Docker base images and pinned Lombok 1.18.46),
restored the audit foreign key (V3 migration), and added admin/Stripe environment scaffolding.

Phase 1 (data foundation). Migrations V4 through V9: account flags and event settings/cover/
expiration, persistent membership status, and the plans (seeded), subscriptions, promo_codes,
promo_code_usage, whitelisted_users, event_analytics, event_visits, and storage_usage tables.
JPA entities and repositories for each. Admin bootstrap via ADMIN_EMAILS at provisioning.

Phase 2 (persistent membership and My Events). Signed-in join creates a durable membership;
GET /api/me/events; leave and owner remove-member endpoints; the My Events dashboard grid.

Phase 3 (dashboards and analytics). Live-aggregation owner and user dashboards, visit tracking
for unique-visitor and active-guest counts, the per-event analytics page with an upload chart.

Phase 4 (moderation and settings). Hide/unhide/archive/restore/soft-delete plus permanent purge,
the owner gallery by state, event settings (named vs anonymous uploads, metadata visibility),
and uploader-name stripping in the public gallery.

Phase 5 (subscriptions and Stripe). Plan and subscription read endpoints, plan-limit enforcement
on event creation and uploads, and Stripe checkout/portal/webhook over the Stripe REST API (no
SDK dependency). Pricing page, plan banner, and quota messaging.

Phase 6 (promo codes and whitelist). Promo redemption (grant-type codes activate a subscription),
admin promo CRUD, and the unlimited-access whitelist (resolved in plan limits).

Phase 7 (admin panel). User management (search, enable/disable, delete, set plan), event
management (archive, remove, transfer), platform analytics, and the role-gated admin UI with five
tabs. Admin is authorized by the persisted user role, seeded from ADMIN_EMAILS.

Phase 8 (UX and PWA). Mobile bottom navigation, an installable service worker (stale-while-
revalidate app shell), plus the skeleton/empty/error states added throughout earlier phases.

Phase 9 (docs and tests). This changelog, ERD/API/ADR updates, and unit tests for hashing, the
admin guard, and promo redeemability.
