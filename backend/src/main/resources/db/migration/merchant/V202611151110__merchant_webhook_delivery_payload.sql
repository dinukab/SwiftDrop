-- Owner: merchant module (ADR 0003, ADR 0004 section 7). A delivery record keeps the exact event body
-- it was created for, so every retry sends and signs the same bytes. The body holds IDs and codes only,
-- never personal data. Nullable: rows created before this column existed have no body, and the sender
-- marks them failed rather than guessing.

ALTER TABLE merchant_webhook_deliveries ADD COLUMN payload TEXT;
