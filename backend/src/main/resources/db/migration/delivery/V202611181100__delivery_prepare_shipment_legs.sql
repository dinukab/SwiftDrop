-- Owner: delivery module (ADR 0003, ADR 0007). Makes shipment_legs usable for the multi-leg workflow.
-- The table is new and unused, so these changes touch no data.
--
-- * A leg's courier is a courier USER id (as everywhere else), not a couriers (profile) id.
-- * completed_at: a leg ends when its courier confirms arrival at the leg's destination hub, or when the
--   parcel is delivered or fails.
-- * One leg per parcel and sequence, and checks on the type and the sequence.

ALTER TABLE shipment_legs DROP CONSTRAINT fk_legs_courier;
ALTER TABLE shipment_legs ADD CONSTRAINT fk_legs_courier FOREIGN KEY (assigned_courier_id) REFERENCES users (id);

ALTER TABLE shipment_legs ADD COLUMN completed_at TIMESTAMPTZ;

ALTER TABLE shipment_legs ADD CONSTRAINT uq_legs_parcel_sequence UNIQUE (parcel_id, sequence);
ALTER TABLE shipment_legs ADD CONSTRAINT ck_legs_sequence CHECK (sequence >= 1);
ALTER TABLE shipment_legs ADD CONSTRAINT ck_legs_leg_type CHECK (leg_type IN ('PICKUP', 'INTER_HUB', 'FINAL_MILE'));
ALTER TABLE shipment_legs ADD CONSTRAINT ck_legs_assignment CHECK ((assigned_courier_id IS NULL) = (assigned_at IS NULL));

-- Legs that wait for a courier at a hub (dispatch queue)
CREATE INDEX ix_legs_waiting ON shipment_legs (source_hub_id, created_at) WHERE assigned_courier_id IS NULL;
