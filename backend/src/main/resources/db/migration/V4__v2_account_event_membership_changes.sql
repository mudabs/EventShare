-- V2: account flags, event settings/cover/expiration, persistent membership status.

ALTER TABLE users
    ADD COLUMN disabled           BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN stripe_customer_id VARCHAR(64),
    ADD COLUMN last_seen_at       TIMESTAMPTZ;

ALTER TABLE events
    ADD COLUMN cover_media_id         UUID REFERENCES media (id) ON DELETE SET NULL,
    ADD COLUMN expires_at             TIMESTAMPTZ,
    ADD COLUMN uploader_visibility    VARCHAR(16) NOT NULL DEFAULT 'NAMED'
                                      CHECK (uploader_visibility IN ('ANONYMOUS','NAMED')),
    ADD COLUMN show_upload_timestamps BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN show_uploader_names    BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN show_upload_stats      BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE event_memberships
    ADD COLUMN status           VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                                CHECK (status IN ('ACTIVE','LEFT','REMOVED')),
    ADD COLUMN last_activity_at TIMESTAMPTZ;

-- Fast lookup of a user's active memberships for the "My Events" screen.
CREATE INDEX ix_memberships_user_status
    ON event_memberships (user_id, status) WHERE user_id IS NOT NULL;
