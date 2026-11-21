-- Owner: dispatch module (ADR 0003). Operational courier profiles linked 1:1 to users (D-28).
-- Phone numbers are personal data and must never be logged or emitted in events.

CREATE TABLE couriers (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL,
    phone        VARCHAR(20) NOT NULL,
    vehicle_type VARCHAR(20) NOT NULL,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    version      BIGINT      NOT NULL DEFAULT 0,

    -- Cross-module reference to identity.users without cascading deletes (ADR 0003 §5).
    CONSTRAINT fk_couriers_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_couriers_user_id UNIQUE (user_id),
    CONSTRAINT ck_couriers_vehicle_type CHECK (vehicle_type IN ('BIKE', 'CAR', 'VAN'))
);

CREATE INDEX ix_couriers_active ON couriers (active);

