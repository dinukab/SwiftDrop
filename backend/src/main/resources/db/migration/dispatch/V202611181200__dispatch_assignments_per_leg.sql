-- Owner: dispatch module (ADR 0003, ADR 0007). A parcel with several legs has several active assignments, one
-- per leg, so "one active assignment per parcel and leg type" is no longer enough (a Uva to Southern parcel has
-- two INTER_HUB legs). The leg is now identified by its sequence number.
--
-- Existing rows are pickup assignments, which is sequence 1.

ALTER TABLE parcel_assignments ADD COLUMN leg_sequence INT NOT NULL DEFAULT 1;
ALTER TABLE parcel_assignments ADD CONSTRAINT ck_parcel_assignments_leg_sequence CHECK (leg_sequence >= 1);

DROP INDEX uq_parcel_assignments_active;
CREATE UNIQUE INDEX uq_parcel_assignments_active ON parcel_assignments (parcel_id, leg_sequence)
    WHERE ended_at IS NULL;
