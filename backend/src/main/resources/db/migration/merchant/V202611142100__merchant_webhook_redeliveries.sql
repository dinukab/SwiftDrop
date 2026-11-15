-- Keep attempt numbers monotonic across series so previous history is never overwritten.
ALTER TABLE merchant_webhook_deliveries
    ADD COLUMN series_attempts INTEGER NOT NULL DEFAULT 0 CHECK (series_attempts >= 0),
    ADD COLUMN redelivery_count INTEGER NOT NULL DEFAULT 0 CHECK (redelivery_count >= 0);
UPDATE merchant_webhook_deliveries SET series_attempts = attempts;

CREATE TABLE merchant_webhook_redeliveries (
    delivery_id UUID NOT NULL REFERENCES merchant_webhook_deliveries(id),
    redelivery_number INTEGER NOT NULL CHECK (redelivery_number > 0),
    requested_by UUID NOT NULL,
    requested_role VARCHAR(32) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    first_attempt INTEGER NOT NULL,
    PRIMARY KEY (delivery_id, redelivery_number)
);
