-- Public demo guardrails follow-up (docs/changes/2026-10-05-public-demo-guardrails.md, DG6/DG10).
--
-- 1. V10 added media.demo_seeded with DEFAULT TRUE so existing rows were treated as
--    seeded. Keeping TRUE as the permanent default would let any insert that does not go
--    through JPA (raw SQL, the deferred worker, tests) silently bypass the demo upload cap.
--    Existing rows keep their backfilled value; only the default changes.
ALTER TABLE media ALTER COLUMN demo_seeded SET DEFAULT FALSE;

-- 2. Per-visitor demo cap: salted SHA-256 of the uploader's IP, stored only for uploads to
--    the public demo event (NULL everywhere else). The nightly demo reset deletes those rows.
ALTER TABLE media ADD COLUMN uploader_ip_hash VARCHAR(64);

CREATE INDEX ix_media_demo_ip ON media (event_id, uploader_ip_hash) WHERE demo_seeded = FALSE;
