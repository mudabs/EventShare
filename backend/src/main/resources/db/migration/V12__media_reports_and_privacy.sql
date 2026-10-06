-- Photo reports and privacy retention (docs/changes/2026-10-05-privacy-terms-and-reporting.md).
--
-- Anyone viewing a gallery can report a photo (for example "this is me, please remove it").
-- Reports are visible to the event host; three open reports from different visitors hide the
-- photo until the host reviews it. Reports are deleted with their media.
CREATE TABLE media_reports (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    media_id          UUID NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    event_id          UUID NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    reason            VARCHAR(24) NOT NULL
                      CHECK (reason IN ('ME_REMOVE','INAPPROPRIATE','COPYRIGHT','CHILD_SAFETY','OTHER')),
    details           VARCHAR(500),
    -- Salted SHA-256 of the reporter's IP: one report per visitor per photo, no raw IP kept.
    reporter_ip_hash  VARCHAR(64) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at       TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_media_report_visitor ON media_reports (media_id, reporter_ip_hash);
CREATE INDEX ix_media_report_open ON media_reports (media_id) WHERE resolved_at IS NULL;
