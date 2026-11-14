-- Owner: identity module (ADR 0003). Splits the combined DISPATCHER_ADMIN role into
-- DISPATCHER (delivery operations) and ADMIN (system administration).
-- Existing DISPATCHER_ADMIN users are mapped to ADMIN: the combined role always included
-- full user-management and approval powers, and no data distinguishes dispatch-only
-- operators. Administrators can create dedicated DISPATCHER accounts afterwards.

INSERT INTO roles (name) VALUES ('DISPATCHER'), ('ADMIN');

UPDATE user_roles SET role_name = 'ADMIN' WHERE role_name = 'DISPATCHER_ADMIN';

DELETE FROM roles WHERE name = 'DISPATCHER_ADMIN';
