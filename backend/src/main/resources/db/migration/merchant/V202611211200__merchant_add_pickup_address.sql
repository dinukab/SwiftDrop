-- Owner: merchant module (ADR 0003). The merchant's one pickup (warehouse) address, where couriers collect its
-- parcels. Registration now requires it; merchants registered before this have none (all columns null) until they
-- add it, and their API orders keep carrying their own pickup address.
--
-- The zone belongs to pricing. merchant may not call pricing (ADR 0003 section 2), so the foreign key is what
-- guarantees the zone exists (cross-module foreign keys are allowed for referential integrity; no cascade).
-- Columns only; no row is rewritten.

ALTER TABLE merchants ADD COLUMN pickup_contact_name VARCHAR(200);
ALTER TABLE merchants ADD COLUMN pickup_phone        VARCHAR(20);
ALTER TABLE merchants ADD COLUMN pickup_line1        VARCHAR(200);
ALTER TABLE merchants ADD COLUMN pickup_line2        VARCHAR(200);
ALTER TABLE merchants ADD COLUMN pickup_city         VARCHAR(100);
ALTER TABLE merchants ADD COLUMN pickup_postal_code  VARCHAR(10);
ALTER TABLE merchants ADD COLUMN pickup_zone_id      UUID;

ALTER TABLE merchants ADD CONSTRAINT fk_merchants_pickup_zone FOREIGN KEY (pickup_zone_id) REFERENCES zones (id);

-- All required parts are set together or not at all (line2 and postal code stay optional)
ALTER TABLE merchants ADD CONSTRAINT ck_merchants_pickup_address CHECK (
    (pickup_contact_name IS NULL AND pickup_phone IS NULL AND pickup_line1 IS NULL AND pickup_city IS NULL
        AND pickup_zone_id IS NULL AND pickup_line2 IS NULL AND pickup_postal_code IS NULL)
    OR (pickup_contact_name IS NOT NULL AND pickup_phone IS NOT NULL AND pickup_line1 IS NOT NULL
        AND pickup_city IS NOT NULL AND pickup_zone_id IS NOT NULL));
