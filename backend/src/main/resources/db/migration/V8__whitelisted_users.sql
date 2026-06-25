CREATE TABLE whitelisted_users (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(320) NOT NULL,
    note       VARCHAR(500),
    granted_by UUID REFERENCES users (id) ON DELETE SET NULL,
    is_active  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_whitelist_email ON whitelisted_users (lower(email));
CREATE TRIGGER trg_whitelist_updated_at
    BEFORE UPDATE ON whitelisted_users FOR EACH ROW EXECUTE FUNCTION set_updated_at();
