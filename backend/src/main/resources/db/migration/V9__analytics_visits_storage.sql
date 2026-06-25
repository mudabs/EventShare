-- Per-event rollup, maintained incrementally by the API/worker.
CREATE TABLE event_analytics (
    event_id         UUID PRIMARY KEY REFERENCES events (id) ON DELETE CASCADE,
    photo_count      INTEGER NOT NULL DEFAULT 0,
    video_count      INTEGER NOT NULL DEFAULT 0,
    total_bytes      BIGINT NOT NULL DEFAULT 0,
    unique_visitors  INTEGER NOT NULL DEFAULT 0,
    active_guests_7d INTEGER NOT NULL DEFAULT 0,
    uploads_today    INTEGER NOT NULL DEFAULT 0,
    uploads_today_date DATE,
    last_activity_at TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_event_analytics_updated_at
    BEFORE UPDATE ON event_analytics FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Visit tracking for unique-visitor / active-guest counts.
CREATE TABLE event_visits (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    visitor_key   VARCHAR(128) NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_event_visit ON event_visits (event_id, visitor_key);
CREATE INDEX ix_event_visit_recent ON event_visits (event_id, last_seen_at);

-- Per-account storage accounting for fast quota checks.
CREATE TABLE storage_usage (
    user_id    UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    used_bytes BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_storage_usage_updated_at
    BEFORE UPDATE ON storage_usage FOR EACH ROW EXECUTE FUNCTION set_updated_at();
