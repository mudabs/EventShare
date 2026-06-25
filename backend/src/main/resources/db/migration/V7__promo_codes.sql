CREATE TABLE promo_codes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code             VARCHAR(40) NOT NULL,
    type             VARCHAR(20) NOT NULL
                     CHECK (type IN ('PERCENT','FIXED','FREE_EVENT','TEMP_PREMIUM','LIFETIME_PREMIUM')),
    value_numeric    NUMERIC(12,2),
    grants_plan_code VARCHAR(24) REFERENCES plans (code),
    duration_days    INTEGER,
    max_redemptions  INTEGER,
    redemptions_used INTEGER NOT NULL DEFAULT 0,
    stripe_coupon_id VARCHAR(128),
    expires_at       TIMESTAMPTZ,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_by       UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_promo_code ON promo_codes (code);
CREATE TRIGGER trg_promo_codes_updated_at
    BEFORE UPDATE ON promo_codes FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE promo_code_usage (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promo_code_id             UUID NOT NULL REFERENCES promo_codes (id) ON DELETE CASCADE,
    user_id                   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    redeemed_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    resulting_subscription_id UUID REFERENCES subscriptions (id) ON DELETE SET NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at                TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_promo_usage ON promo_code_usage (promo_code_id, user_id);
CREATE TRIGGER trg_promo_usage_updated_at
    BEFORE UPDATE ON promo_code_usage FOR EACH ROW EXECUTE FUNCTION set_updated_at();
