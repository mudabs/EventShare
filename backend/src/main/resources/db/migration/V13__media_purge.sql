-- Automatic purge of deleted media (docs/changes/2026-10-05-media-purge-admin-chart-demo-promo.md).
--
-- media.deleted_at now records when an item entered moderation_state = 'DELETED' (set by the
-- entity, cleared on restore). MediaPurgeJob erases items deleted more than 30 days ago, and
-- events soft-deleted more than 30 days ago, from R2 and the database.

-- Backfill: items already deleted get their last update time as the deletion time.
UPDATE media SET deleted_at = updated_at
 WHERE moderation_state = 'DELETED' AND deleted_at IS NULL;

CREATE INDEX ix_media_purge ON media (deleted_at) WHERE moderation_state = 'DELETED';
CREATE INDEX ix_events_purge ON events (deleted_at) WHERE deleted_at IS NOT NULL;
