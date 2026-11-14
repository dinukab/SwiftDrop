-- Owner: merchant module. API keys for automated order intake (MER-2).
-- Secrets are stored hashed (BCrypt), never in plain text.

CREATE TABLE merchant_api_keys (
    id           UUID         PRIMARY KEY,
    merchant_id  UUID         NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,
    -- First 8 characters of the raw key body for indexing and constant-time prefix match
    key_prefix   VARCHAR(8)   NOT NULL,
    -- BCrypt hash of the full raw API key
    key_hash     VARCHAR(255) NOT NULL,
    label        VARCHAR(100) NOT NULL,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_merchant_api_keys_prefix UNIQUE (key_prefix)
);

CREATE INDEX ix_merchant_api_keys_merchant ON merchant_api_keys (merchant_id, is_active);

