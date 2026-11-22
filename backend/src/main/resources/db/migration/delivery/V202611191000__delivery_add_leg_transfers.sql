-- Owner: delivery module (ADR 0003, ADR 0007 amendment). Direct hub-to-hub transfers: an INTER_HUB leg is
-- transferred by the sending hub's dispatcher and received by the receiving hub's dispatcher, with no courier.
--
-- * shipment_legs: who sent the parcel and when, and who confirmed receipt and when. Transfer and receipt are
--   separate facts; completed_at is set by the receipt (or, for legacy courier legs, by the courier's arrival).
-- * parcels: between hubs the parcel has no courier (the previous courier lets go at the transfer, the final-mile
--   courier is set when the receiving dispatcher assigns one), so the courier is no longer required while the
--   parcel is IN_TRANSIT, HUB_ARRIVED or HUB_SORTED.
-- * parcel_status_history: the dispatcher is now an actor of status changes (DISPATCHER_ADMIN stays for old rows).

ALTER TABLE shipment_legs ADD COLUMN transferred_at TIMESTAMPTZ;
ALTER TABLE shipment_legs ADD COLUMN transferred_by UUID;
ALTER TABLE shipment_legs ADD COLUMN received_at TIMESTAMPTZ;
ALTER TABLE shipment_legs ADD COLUMN received_by UUID;

ALTER TABLE shipment_legs ADD CONSTRAINT fk_legs_transferred_by FOREIGN KEY (transferred_by) REFERENCES users (id);
ALTER TABLE shipment_legs ADD CONSTRAINT fk_legs_received_by FOREIGN KEY (received_by) REFERENCES users (id);
ALTER TABLE shipment_legs ADD CONSTRAINT ck_legs_transfer CHECK ((transferred_at IS NULL) = (transferred_by IS NULL));
ALTER TABLE shipment_legs ADD CONSTRAINT ck_legs_receipt CHECK ((received_at IS NULL) = (received_by IS NULL));
-- Only a hub-to-hub leg is transferred, and a parcel cannot be received before it was sent
ALTER TABLE shipment_legs ADD CONSTRAINT ck_legs_transfer_type CHECK (transferred_at IS NULL OR leg_type = 'INTER_HUB');
ALTER TABLE shipment_legs ADD CONSTRAINT ck_legs_receipt_after_transfer CHECK (received_at IS NULL OR transferred_at IS NOT NULL);

-- Parcels on their way to a hub (dispatcher dashboard): sent and not yet received
CREATE INDEX ix_legs_incoming ON shipment_legs (destination_hub_id, transferred_at)
    WHERE transferred_at IS NOT NULL AND received_at IS NULL;

ALTER TABLE parcels DROP CONSTRAINT ck_parcels_courier_after_created;
ALTER TABLE parcels ADD CONSTRAINT ck_parcels_courier_after_created CHECK
    (status IN ('CREATED', 'PAUSED', 'CANCELLED', 'HUB_ARRIVED', 'HUB_SORTED', 'IN_TRANSIT')
        OR assigned_courier_id IS NOT NULL);

ALTER TABLE parcel_status_history DROP CONSTRAINT ck_parcel_status_history_actor_role;
ALTER TABLE parcel_status_history ADD CONSTRAINT ck_parcel_status_history_actor_role CHECK (actor_role IN
    ('CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER', 'DISPATCHER_ADMIN'));
