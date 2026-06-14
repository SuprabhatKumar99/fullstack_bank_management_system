-- ============================================================
-- V2: Refresh Tokens
-- Persisted for revocation support (logout / token family rotation).
-- Redis is the fast lookup; this table is the source of truth.
-- ============================================================

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash  VARCHAR(255)    NOT NULL UNIQUE,   -- SHA-256 of the raw token
    user_id     UUID            NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    issued_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ     NOT NULL,
    revoked     BOOLEAN         NOT NULL DEFAULT FALSE,
    revoked_at  TIMESTAMPTZ,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(512)
);

CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at)
    WHERE revoked = FALSE;

-- ── Nightly cleanup job: delete expired & revoked tokens ──────
-- (triggered from Spring @Scheduled in TokenCleanupJob)