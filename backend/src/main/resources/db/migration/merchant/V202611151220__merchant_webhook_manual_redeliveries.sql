-- Owner: merchant module (ADR 0004 section 7). A merchant can ask for a failed webhook delivery to be
-- sent again. Each manual redelivery is counted; at most three are allowed per delivery, so a broken
-- endpoint cannot be flooded by repeated requests.

ALTER TABLE merchant_webhook_deliveries
    ADD COLUMN manual_redeliveries INT NOT NULL DEFAULT 0;

ALTER TABLE merchant_webhook_deliveries
    ADD CONSTRAINT ck_webhook_deliveries_manual_redeliveries
        CHECK (manual_redeliveries BETWEEN 0 AND 3);
