-- Subscription plan catalogue (data-driven so admins can tune limits). Limits that
-- are NULL mean "unlimited". storage_bytes is the per-account cap; the *_per_event
-- limits apply to each event the account owns.
CREATE TABLE plans (
    code                 VARCHAR(24) PRIMARY KEY,
    name                 VARCHAR(80) NOT NULL,
    price_cents          INTEGER NOT NULL,
    billing_interval     VARCHAR(16) NOT NULL
                         CHECK (billing_interval IN ('NONE','MONTH','YEAR','ONE_TIME')),
    stripe_price_id      VARCHAR(128),
    max_events           INTEGER,
    max_guests_per_event INTEGER,
    max_photos_per_event INTEGER,
    max_videos_per_event INTEGER,
    storage_bytes        BIGINT,
    zip_export           BOOLEAN NOT NULL DEFAULT FALSE,
    advanced_analytics   BOOLEAN NOT NULL DEFAULT FALSE,
    priority_processing  BOOLEAN NOT NULL DEFAULT FALSE,
    retention_months     INTEGER,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO plans
  (code, name, price_cents, billing_interval, max_events, max_guests_per_event,
   max_photos_per_event, max_videos_per_event, storage_bytes, zip_export,
   advanced_analytics, priority_processing, retention_months, is_active) VALUES
  ('FREE',        'Free',           0,    'NONE',     1,    5,    500,   20,   5368709120,   FALSE, FALSE, FALSE, NULL, TRUE),
  ('BASIC',       'Basic Event',    1900, 'MONTH',    NULL, 100,  5000,  250,  53687091200,  TRUE,  TRUE,  FALSE, 12,   TRUE),
  ('WEDDING_PRO', 'Wedding Pro',    4900, 'MONTH',    NULL, NULL, 20000, 2000, 214748364800, TRUE,  TRUE,  TRUE,  24,   TRUE),
  ('LIFETIME',    'Lifetime Event', 9900, 'ONE_TIME', NULL, NULL, NULL,  NULL, NULL,         TRUE,  TRUE,  TRUE,  NULL, TRUE);
