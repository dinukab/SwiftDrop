-- Owner: identity module (ADR 0003). Courier accounts are now created directly by an ADMIN
-- through staff management; the public-registration approval workflow is removed.
-- courier_approvals only held workflow metadata, not courier account data: users, roles and
-- audit fields (created_at on users) are untouched. Any rows here were approval decisions,
-- which no longer apply.

DROP TABLE courier_approvals;
