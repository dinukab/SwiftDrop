-- Owner: merchant module (ADR 0003). Merchant profiles and approval workflow (MER-1).

CREATE TABLE merchants (
    id                  UUID         PRIMARY KEY,
    -- Cross-module reference by ID; foreign key to users (ADR 0003)
    user_id             UUID         NOT NULL UNIQUE REFERENCES users (id),
    business_name       VARCHAR(150) NOT NULL,
    registration_number VARCHAR(50)  NOT NULL UNIQUE,
    contact_email       VARCHAR(254) NOT NULL,
    contact_phone       VARCHAR(30)  NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING_APPROVAL',
    -- Who approved or rejected (Dispatcher/Admin user ID)
    reviewed_by         UUID         REFERENCES users (id),
    reviewed_at         TIMESTAMPTZ,
    rejection_reason    TEXT,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_merchants_status CHECK (
        status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'SUSPENDED'))
);

-- Admin review list: filter by status, ordered by created_at
CREATE INDEX ix_merchants_status_created ON merchants (status, created_at);

