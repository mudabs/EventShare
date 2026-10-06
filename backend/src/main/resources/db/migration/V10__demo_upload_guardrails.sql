-- Existing rows predate the guardrail and are treated as baseline/seeded. New
-- JPA-created user reservations explicitly write FALSE; DemoSeeder writes TRUE.
-- change 2026-10-05-DG
-- User reservations intentionally remain counted even after deletion so a guest
-- cannot bypass the cap by uploading and deleting repeatedly.
ALTER TABLE media
    ADD COLUMN demo_seeded BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX ix_media_demo_uploads ON media (event_id, demo_seeded);
