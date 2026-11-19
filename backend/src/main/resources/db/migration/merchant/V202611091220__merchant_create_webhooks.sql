-- Owner: merchant module. Webhook endpoints and delivery history (MER-3, ADR 0004 §7).

CREATE TABLE merchant_webhook_endpoints (
    id               UUID        PRIMARY KEY,
    merchant_id      UUID        NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,
    url              TEXT        NOT NULL,
    -- Encrypted signing secret (ADR 0004 §7.2). Never stored in plain text.
    secret_encrypted TEXT        NOT NULL,
    is_active        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL
);

CREATE TABLE merchant_webhook_deliveries (
    id               UUID        PRIMARY KEY,
    endpoint_id      UUID        NOT NULL REFERENCES merchant_webhook_endpoints (id) ON DELETE CASCADE,
    merchant_id      UUID        NOT NULL,
    -- Cross-module parcel reference by ID only (ADR 0003)
    parcel_id        UUID        NOT NULL,
    -- Event ID from parcel.status-changed (ADR 0004 §2)
    event_id         UUID        NOT NULL,
    attempt_count    INT         NOT NULL DEFAULT 0,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_attempt_at  TIMESTAMPTZ,
    next_attempt_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_http_status INT,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,

    -- Idempotency: one delivery record per event per endpoint (ADR 0004 §7.1)
    CONSTRAINT uq_webhook_deliveries_event_endpoint UNIQUE (event_id, endpoint_id),
    CONSTRAINT ck_webhook_deliveries_status CHECK (
        status IN ('PENDING', 'IN_FLIGHT', 'SUCCESS', 'FAILED'))
);

CREATE INDEX ix_webhook_deliveries_pending
    ON merchant_webhook_deliveries (next_attempt_at)
    WHERE status = 'PENDING';

