-- ============================================================
-- V1: Auth Users & Roles
-- Owns credential data only. Profile data lives in Customer Service.
-- ============================================================

CREATE TABLE auth_users (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    username            VARCHAR(50)     NOT NULL UNIQUE,
    email               VARCHAR(100)    NOT NULL UNIQUE,
    password_hash       VARCHAR(255)    NOT NULL,
    role                VARCHAR(30)     NOT NULL DEFAULT 'ROLE_CUSTOMER',
    enabled             BOOLEAN         NOT NULL DEFAULT TRUE,
    account_non_expired BOOLEAN         NOT NULL DEFAULT TRUE,
    account_non_locked  BOOLEAN         NOT NULL DEFAULT TRUE,
    credentials_non_expired BOOLEAN     NOT NULL DEFAULT TRUE,
    failed_login_attempts   INT         NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,
    last_login_at       TIMESTAMPTZ,
    customer_id         UUID,           -- FK reference to customer_service.customers (logical, not enforced)
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ── Indexes ──────────────────────────────────────────────────
CREATE INDEX idx_auth_users_email    ON auth_users(email);
CREATE INDEX idx_auth_users_username ON auth_users(username);
CREATE INDEX idx_auth_users_role     ON auth_users(role);

-- ── Audit trigger ─────────────────────────────────────────────
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_auth_users_updated_at
    BEFORE UPDATE ON auth_users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ── Default admin user (password: Admin@123 – bcrypt) ─────────
INSERT INTO auth_users (
    username, email, password_hash, role
) VALUES (
    'admin',
    'admin@cbs.local',
    '$2a$12$K8Dy/5x9P9c7lHF2v0kFyuoQsS1dHyNg1h8bkWBc9mOK.kJpC3xBu',
    'ROLE_ADMIN'
);