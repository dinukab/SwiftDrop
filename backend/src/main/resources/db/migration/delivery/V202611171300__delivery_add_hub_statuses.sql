-- Owner: delivery module (ADR 0003). The hub workflow adds the statuses HUB_ARRIVED and HUB_SORTED
-- (ParcelStatus, ParcelLifecycle). The CHECK constraints widened for PAUSED and CANCELLED in
-- V202611151000 list the statuses explicitly, so without this migration the first hub arrival fails
-- with a constraint violation on PostgreSQL.
--
-- Widens three CHECK constraints; no row is rewritten. ck_parcels_courier_after_created needs no
-- change: a parcel at a hub always has a courier. One-way in practice, like V202611151000.

ALTER TABLE parcels DROP CONSTRAINT ck_parcels_status;
ALTER TABLE parcels ADD CONSTRAINT ck_parcels_status CHECK (status IN
    ('CREATED', 'PICKED_UP', 'HUB_ARRIVED', 'HUB_SORTED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY',
     'DELIVERED', 'FAILED', 'PAUSED', 'CANCELLED'));

-- A history entry can leave a hub status and can enter any non-initial status
ALTER TABLE parcel_status_history DROP CONSTRAINT ck_parcel_status_history_from_status;
ALTER TABLE parcel_status_history ADD CONSTRAINT ck_parcel_status_history_from_status CHECK (from_status IN
    ('CREATED', 'PICKED_UP', 'HUB_ARRIVED', 'HUB_SORTED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'PAUSED'));

ALTER TABLE parcel_status_history DROP CONSTRAINT ck_parcel_status_history_to_status;
ALTER TABLE parcel_status_history ADD CONSTRAINT ck_parcel_status_history_to_status CHECK (to_status IN
    ('CREATED', 'PICKED_UP', 'HUB_ARRIVED', 'HUB_SORTED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY',
     'DELIVERED', 'FAILED', 'PAUSED', 'CANCELLED'));
