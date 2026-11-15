-- Owner: delivery module (ADR 0003). A merchant can hold (PAUSED) or cancel (CANCELLED) its parcel
-- before pickup, and release a hold (PAUSED -> CREATED) (ADR 0006 amendment).
--
-- Widens four CHECK constraints; no row is rewritten. This is one-way in practice: once a parcel
-- or history row uses PAUSED or CANCELLED, the old constraints can no longer be restored without
-- first fixing that data.

ALTER TABLE parcels DROP CONSTRAINT ck_parcels_status;
ALTER TABLE parcels ADD CONSTRAINT ck_parcels_status CHECK (status IN
    ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED', 'PAUSED', 'CANCELLED'));

-- A courier is still required before PICKED_UP. A held or cancelled parcel never left CREATED, so
-- it may have no courier.
ALTER TABLE parcels DROP CONSTRAINT ck_parcels_courier_after_created;
ALTER TABLE parcels ADD CONSTRAINT ck_parcels_courier_after_created CHECK
    (status IN ('CREATED', 'PAUSED', 'CANCELLED') OR assigned_courier_id IS NOT NULL);

-- A history entry can leave PAUSED (resume or cancel) and can enter PAUSED or CANCELLED
ALTER TABLE parcel_status_history DROP CONSTRAINT ck_parcel_status_history_from_status;
ALTER TABLE parcel_status_history ADD CONSTRAINT ck_parcel_status_history_from_status CHECK (from_status IN
    ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'PAUSED'));

ALTER TABLE parcel_status_history DROP CONSTRAINT ck_parcel_status_history_to_status;
ALTER TABLE parcel_status_history ADD CONSTRAINT ck_parcel_status_history_to_status CHECK (to_status IN
    ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED', 'PAUSED', 'CANCELLED'));
