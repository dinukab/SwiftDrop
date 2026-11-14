-- Owner: identity module (ADR 0003). Single active session per user: each login increments
-- users.session_version and the JWT carries it as the "sv" claim; the resource server rejects
-- tokens whose version no longer matches, so a newer login invalidates older sessions.

ALTER TABLE users ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;
