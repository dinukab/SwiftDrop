-- Owner: delivery module (ADR 0003, ADR 0004 section 3). The outbox relay has never run, so every
-- event written before it exists is unpublished. Without this, the first relay run would publish
-- all of them, and the merchant consumer would send webhooks for old orders. Those events are
-- marked as published here so the relay only sends events created after this change.
--
-- Data change, not a schema change. It is one-way: the original unpublished state is not kept.

UPDATE parcel_outbox_events
SET published_at = now()
WHERE published_at IS NULL;
