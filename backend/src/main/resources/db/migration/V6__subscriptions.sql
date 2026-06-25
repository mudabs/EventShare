-- One active subscription per user (per-user plan model).
CREATE TABLE subscriptions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan_code              VARCHAR(24) NOT NULL REFERENCES plans (code),
    status                 VARCHAR(16) NOT NULL
                           CHECK (status IN ('ACTIVE','TRIALING','PAST_DUE','CANCELED','EXPIRED')),
    stripe_subscription_id VARCHAR(128),
    current_period_end     TIMESTAMPTZ,
    cancel_at_period_end   BOOLEAN NOT NULL DEFAULT FALSE,
    source                 VARCHAR(16) NOT NULL
                           CHECK (source IN ('STRIPE','PROMO','WHITELIST','ADMIN')),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_subscription_active_user ON subscriptions (user_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_subscription_stripe ON subscriptions (stripe_subscription_id);
CREATE TRIGGER trg_subscriptions_updated_at
    BEFORE UPDATE ON subscriptions FOR EACH ROW EXECUTE FUNCTION set_updated_at();
