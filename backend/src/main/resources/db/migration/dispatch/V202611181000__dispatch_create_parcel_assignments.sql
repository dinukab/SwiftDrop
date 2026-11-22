-- Owner: dispatch module (ADR 0003). Who assigned which courier to a parcel, when, and for which hub:
-- the dispatcher's assignment history. Today a parcel has one courier, assigned while it is CREATED, so
-- every row is a PICKUP assignment; leg_type is here so inter-hub and final-mile legs can follow without
-- another migration.
--
-- At most one ACTIVE (not ended) assignment per parcel and leg, enforced by the database. A
-- reassignment ends the previous row (REASSIGNED) and adds a new one.
--
-- parcel_id has no foreign key: it points at delivery.parcels and dispatch does not reach into delivery's
-- tables (ADR 0003). Parcels assigned before this migration have no history rows.

CREATE TABLE parcel_assignments (
    id                 UUID        PRIMARY KEY,
    parcel_id          UUID        NOT NULL,
    leg_type           VARCHAR(20) NOT NULL DEFAULT 'PICKUP',
    courier_user_id    UUID        NOT NULL,
    dispatcher_user_id UUID        NOT NULL,
    hub_id             UUID        NOT NULL,
    assigned_at        TIMESTAMPTZ NOT NULL,
    ended_at           TIMESTAMPTZ,
    end_reason         VARCHAR(20),

    CONSTRAINT fk_parcel_assignments_courier FOREIGN KEY (courier_user_id) REFERENCES users (id),
    CONSTRAINT fk_parcel_assignments_dispatcher FOREIGN KEY (dispatcher_user_id) REFERENCES users (id),
    CONSTRAINT fk_parcel_assignments_hub FOREIGN KEY (hub_id) REFERENCES hubs (id),
    CONSTRAINT ck_parcel_assignments_leg_type CHECK (leg_type IN ('PICKUP', 'INTER_HUB', 'FINAL_MILE')),
    CONSTRAINT ck_parcel_assignments_end_reason CHECK (end_reason IN ('REASSIGNED')),
    CONSTRAINT ck_parcel_assignments_ended CHECK ((ended_at IS NULL) = (end_reason IS NULL))
);

CREATE UNIQUE INDEX uq_parcel_assignments_active ON parcel_assignments (parcel_id, leg_type)
    WHERE ended_at IS NULL;

CREATE INDEX ix_parcel_assignments_parcel ON parcel_assignments (parcel_id, assigned_at);
CREATE INDEX ix_parcel_assignments_courier ON parcel_assignments (courier_user_id);
