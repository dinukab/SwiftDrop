-- Owner: merchant module (ADR 0003). Merchant registration and approval (D-22 / SD-401).
-- user_id references identity.users by ID only (no cross-module FK).

CREATE TABLE merchants (
    id                UUID                        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id           UUID                        NOT NULL,
    business_name     VARCHAR(200)                NOT NULL,
    contact_phone     VARCHAR(20),
    status            VARCHAR(20)                 NOT NULL DEFAULT 'PENDING'
                          CONSTRAINT merchants_status_check
                              CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    reviewed_by       UUID,
    reviewed_at       TIMESTAMPTZ,
    rejection_reason  VARCHAR(500),
    created_at        TIMESTAMPTZ                 NOT NULL DEFAULT now(),
    version           BIGINT                      NOT NULL DEFAULT 0,
    CONSTRAINT uq_merchants_user_id UNIQUE (user_id)
);

CREATE INDEX ix_merchants_status ON merchants (status);
