-- Owner: identity module. Soft delete of user accounts: a deleted user keeps its row (parcels, history and
-- profiles still reference it) but is anonymised, disabled and hidden from the user directory.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
