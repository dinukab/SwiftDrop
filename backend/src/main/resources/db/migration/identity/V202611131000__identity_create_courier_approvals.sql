-- Owner: identity module (ADR 0003). Courier registration approval state (user management).
-- One row per courier user; admin-created couriers have no row and are treated as approved.

CREATE TABLE courier_approvals (
    user_id           UUID                        NOT NULL PRIMARY KEY
                         REFERENCES users (id) ON DELETE CASCADE,
    status            VARCHAR(20)                 NOT NULL DEFAULT 'PENDING'
                         CONSTRAINT courier_approvals_status_check
                             CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by       UUID,
    reviewed_at       TIMESTAMPTZ,
    rejection_reason  VARCHAR(500),
    created_at        TIMESTAMPTZ                 NOT NULL DEFAULT now(),
    version           BIGINT                      NOT NULL DEFAULT 0
);

CREATE INDEX ix_courier_approvals_status ON courier_approvals (status);
