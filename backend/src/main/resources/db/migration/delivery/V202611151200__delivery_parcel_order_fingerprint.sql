-- Owner: delivery module (ADR 0003). A fingerprint of the merchant order each parcel was created from,
-- so a repeated order reference can be told apart: the same content returns the existing parcel, and
-- different content is refused (409). Holds a SHA-256 hex digest of the order fields, never the fields.
-- Nullable: parcels created before this column existed have no fingerprint and keep the old behaviour.

ALTER TABLE parcels ADD COLUMN order_fingerprint VARCHAR(64);
