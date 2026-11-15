-- Owner: merchant module (ADR 0003, ADR 0004 section 7.4). A key can carry a signing secret, so the
-- merchant's store can sign each request (HMAC needs the secret itself, so it is encrypted, not hashed).
-- Nullable: keys created before this column existed have no secret. They keep working while request
-- signatures are optional, and must be created again once they are required.

ALTER TABLE merchant_api_keys ADD COLUMN signing_secret_encrypted TEXT;
