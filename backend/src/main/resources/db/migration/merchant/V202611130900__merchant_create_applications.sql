-- Owner: merchant module (ADR 0003). Merchant registration applications awaiting admin review.
-- Reconstructed to match the schema already applied to existing databases (flyway repair target).
-- user_id references identity.users by ID only (no cross-module FK).

CREATE TABLE merchant_applications (
    id               UUID                        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id          UUID,
    business_name    VARCHAR(200)                NOT NULL,
    contact_name     VARCHAR(200)                NOT NULL,
    contact_email    VARCHAR(254)                NOT NULL,
    status           VARCHAR(20)                 NOT NULL DEFAULT 'PENDING'
                         CONSTRAINT merchant_applications_status_check
                             CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    rejection_reason TEXT,
    applied_at       TIMESTAMPTZ                 NOT NULL DEFAULT now(),
    reviewed_at      TIMESTAMPTZ,
    reviewed_by      UUID
);

CREATE INDEX merchant_applications_contact_email_idx ON merchant_applications (contact_email);
CREATE INDEX merchant_applications_status_idx ON merchant_applications (status);
