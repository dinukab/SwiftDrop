-- Extend the earlier merchant webhook tables. Legacy rows have no authenticated payload or
-- usable encrypted secret, so they stay visible but cannot be delivered or redelivered.
ALTER TABLE merchant_webhook_endpoints
    ALTER COLUMN secret_encrypted SET DEFAULT '',
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN key_id VARCHAR(64) NOT NULL DEFAULT 'legacy-unavailable',
    ADD COLUMN nonce BYTEA NOT NULL DEFAULT decode('', 'hex'),
    ADD COLUMN ciphertext BYTEA NOT NULL DEFAULT decode('', 'hex');
CREATE INDEX merchant_webhook_endpoints_owner ON merchant_webhook_endpoints(merchant_id);

ALTER TABLE merchant_webhook_deliveries
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN payload TEXT NOT NULL DEFAULT '{}',
    ADD COLUMN state VARCHAR(32) NOT NULL DEFAULT 'FAILED'
        CHECK (state IN ('PENDING', 'SENDING', 'SUCCEEDED', 'FAILED', 'SECRET_UNAVAILABLE')),
    ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    ADD COLUMN lease_token UUID;
UPDATE merchant_webhook_deliveries SET attempts = attempt_count;
ALTER TABLE merchant_webhook_deliveries ALTER COLUMN state SET DEFAULT 'PENDING';
CREATE INDEX merchant_webhook_deliveries_due ON merchant_webhook_deliveries(next_attempt_at)
    WHERE state IN ('PENDING', 'SENDING', 'SECRET_UNAVAILABLE');

CREATE TABLE merchant_webhook_attempts (
    delivery_id UUID NOT NULL REFERENCES merchant_webhook_deliveries(id),
    attempt INTEGER NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    http_status INTEGER,
    duration_ms BIGINT NOT NULL,
    next_attempt_at TIMESTAMPTZ,
    PRIMARY KEY(delivery_id, attempt)
);
