-- Owned by merchant; inbox state is not a replacement for delivery history.
CREATE TABLE merchant_event_state (
    aggregate_id UUID PRIMARY KEY,
    last_sequence INTEGER NOT NULL DEFAULT 0 CHECK (last_sequence >= 0),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    stuck BOOLEAN NOT NULL DEFAULT FALSE,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE merchant_event_processed (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE merchant_event_pending (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL REFERENCES merchant_event_state (aggregate_id),
    sequence INTEGER NOT NULL CHECK (sequence >= 1),
    payload TEXT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    UNIQUE (aggregate_id, sequence)
);
CREATE INDEX ix_merchant_event_pending_received ON merchant_event_pending (received_at);
