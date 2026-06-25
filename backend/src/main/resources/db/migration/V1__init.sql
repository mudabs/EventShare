-- =============================================================================
-- EventShare initial schema (V1)
-- PostgreSQL 14+ (uses built-in gen_random_uuid(), jsonb, partial indexes).
--
-- Conventions:
--   * UUID primary keys (opaque, non-enumerable, safe to expose in URLs).
--   * created_at / updated_at on mutable tables; updated_at maintained by trigger.
--   * deleted_at for soft delete (NULL = live row).
--   * Enumerations modelled as VARCHAR + CHECK so they evolve without ALTER TYPE.
-- =============================================================================

-- Generic trigger to maintain updated_at on UPDATE.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- -----------------------------------------------------------------------------
-- users: authenticated (Clerk) principals. Hosts and admins have rows here.
-- Anonymous guests do NOT require a row (see event_memberships).
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_user_id VARCHAR(255) NOT NULL,
    email         VARCHAR(320),
    display_name  VARCHAR(255),
    avatar_url    VARCHAR(1024),
    role          VARCHAR(16) NOT NULL DEFAULT 'HOST'
                  CHECK (role IN ('ADMIN', 'HOST', 'GUEST')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_users_clerk_user_id ON users (clerk_user_id);
CREATE INDEX ix_users_email ON users (email);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- -----------------------------------------------------------------------------
-- events: a shared media room owned by a host.
-- -----------------------------------------------------------------------------
CREATE TABLE events (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id               UUID NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    name                  VARCHAR(200) NOT NULL,
    description           TEXT,
    event_type            VARCHAR(24) NOT NULL
                          CHECK (event_type IN ('WEDDING','FAMILY','GRADUATION',
                                 'CHURCH','CONFERENCE','BIRTHDAY','REUNION','OTHER')),
    invite_code           VARCHAR(24) NOT NULL,
    status                VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                          CHECK (status IN ('ACTIVE','ARCHIVED')),
    event_date            DATE,
    allow_guest_downloads BOOLEAN NOT NULL DEFAULT TRUE,
    auto_approve          BOOLEAN NOT NULL DEFAULT TRUE,
    max_upload_bytes      BIGINT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_events_invite_code ON events (invite_code);
CREATE INDEX ix_events_host_id ON events (host_id);
CREATE INDEX ix_events_status ON events (status);

CREATE TRIGGER trg_events_updated_at
    BEFORE UPDATE ON events
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- -----------------------------------------------------------------------------
-- event_memberships: links a participant to an event.
--   * user_id set        -> authenticated participant (host / moderator / guest)
--   * user_id NULL        -> anonymous guest identified only by display name
-- -----------------------------------------------------------------------------
CREATE TABLE event_memberships (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id           UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    user_id            UUID REFERENCES users (id) ON DELETE SET NULL,
    guest_display_name VARCHAR(120),
    role               VARCHAR(16) NOT NULL DEFAULT 'GUEST'
                       CHECK (role IN ('HOST','MODERATOR','GUEST')),
    joined_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ
);
-- One membership per authenticated user per event (guests are not deduplicated).
CREATE UNIQUE INDEX ux_membership_event_user
    ON event_memberships (event_id, user_id)
    WHERE user_id IS NOT NULL;
CREATE INDEX ix_membership_event ON event_memberships (event_id);

CREATE TRIGGER trg_memberships_updated_at
    BEFORE UPDATE ON event_memberships
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- -----------------------------------------------------------------------------
-- media: one uploaded photo or video. Bytes live in R2; this is metadata only.
--   status           -> processing lifecycle (PENDING..PROCESSED/FAILED)
--   moderation_state -> host-controlled visibility (VISIBLE/HIDDEN/ARCHIVED/DELETED)
-- -----------------------------------------------------------------------------
CREATE TABLE media (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id               UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    uploader_membership_id UUID REFERENCES event_memberships (id) ON DELETE SET NULL,
    uploader_user_id       UUID REFERENCES users (id) ON DELETE SET NULL,
    uploader_display_name  VARCHAR(120),
    original_filename      VARCHAR(512),
    content_type           VARCHAR(128) NOT NULL,
    media_type             VARCHAR(8) NOT NULL CHECK (media_type IN ('PHOTO','VIDEO')),
    size_bytes             BIGINT,
    object_key             VARCHAR(700) NOT NULL,
    thumbnail_key          VARCHAR(700),
    sha256                 CHAR(64),
    width                  INTEGER,
    height                 INTEGER,
    duration_seconds       NUMERIC(10,3),
    status                 VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                           CHECK (status IN ('PENDING','UPLOADED','PROCESSING','PROCESSED','FAILED')),
    moderation_state       VARCHAR(16) NOT NULL DEFAULT 'VISIBLE'
                           CHECK (moderation_state IN ('VISIBLE','HIDDEN','ARCHIVED','DELETED')),
    is_duplicate           BOOLEAN NOT NULL DEFAULT FALSE,
    duplicate_of_id        UUID REFERENCES media (id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_media_object_key ON media (object_key);
-- Primary gallery access path: newest-visible-first within an event.
CREATE INDEX ix_media_gallery ON media (event_id, moderation_state, created_at DESC);
CREATE INDEX ix_media_status ON media (status);
CREATE INDEX ix_media_sha_event ON media (event_id, sha256);
CREATE INDEX ix_media_duplicate_of ON media (duplicate_of_id);

CREATE TRIGGER trg_media_updated_at
    BEFORE UPDATE ON media
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- -----------------------------------------------------------------------------
-- downloads: asynchronous export jobs (single / multi / full-event ZIP).
-- -----------------------------------------------------------------------------
CREATE TABLE downloads (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id             UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    requested_by_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    type                 VARCHAR(16) NOT NULL CHECK (type IN ('SINGLE','MULTI','EVENT_EXPORT')),
    status               VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING','PROCESSING','READY','FAILED','EXPIRED')),
    object_key           VARCHAR(700),
    item_count           INTEGER,
    size_bytes           BIGINT,
    error_message        TEXT,
    expires_at           TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ
);
CREATE INDEX ix_downloads_event ON downloads (event_id);
CREATE INDEX ix_downloads_status ON downloads (status);

CREATE TRIGGER trg_downloads_updated_at
    BEFORE UPDATE ON downloads
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- -----------------------------------------------------------------------------
-- notifications: host-facing in-app notifications (e.g. export ready).
-- -----------------------------------------------------------------------------
CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    event_id   UUID REFERENCES events (id) ON DELETE CASCADE,
    type       VARCHAR(48) NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id, read_at);

CREATE TRIGGER trg_notifications_updated_at
    BEFORE UPDATE ON notifications
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- -----------------------------------------------------------------------------
-- audit_logs: append-only record of security/moderation-relevant actions.
-- No updated_at / deleted_at: audit rows are immutable by design.
-- -----------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      UUID REFERENCES events (id) ON DELETE SET NULL,
    actor_user_id UUID REFERENCES users (id) ON DELETE SET NULL,
    actor_label   VARCHAR(160),
    action        VARCHAR(64) NOT NULL,
    target_type   VARCHAR(48),
    target_id     UUID,
    metadata      JSONB,
    ip_address    VARCHAR(64),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_event_time ON audit_logs (event_id, created_at DESC);
CREATE INDEX ix_audit_actor ON audit_logs (actor_user_id);
