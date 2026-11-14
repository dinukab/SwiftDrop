CREATE TABLE merchant_webhook_endpoints (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    url VARCHAR(2048) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    key_id VARCHAR(64) NOT NULL,
    nonce BYTEA NOT NULL,
    ciphertext BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX merchant_webhook_endpoints_owner ON merchant_webhook_endpoints(merchant_id);
CREATE TABLE merchant_webhook_deliveries (
    id UUID PRIMARY KEY,
    endpoint_id UUID NOT NULL REFERENCES merchant_webhook_endpoints(id),
    event_id UUID NOT NULL,
    payload TEXT NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING', 'SENDING', 'SUCCEEDED', 'FAILED', 'SECRET_UNAVAILABLE')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_token UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(event_id, endpoint_id)
);
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
