-- Owner: dispatch module (ADR 0003). Hubs are reference data: one hub per province (the nine pricing
-- zones), Colombo (Western Province) being the Main Hub. The code is the province code, for example
-- LK-UP, NOT a zone UUID: zone IDs are generated per environment and dispatch must not read pricing
-- tables. A dispatcher is bound to one hub; hub permission checks use this table, never browser input.

CREATE TABLE hubs (
    id         UUID         PRIMARY KEY,
    code       VARCHAR(10)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    main       BOOLEAN      NOT NULL DEFAULT FALSE,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_hubs_code UNIQUE (code)
);

-- At most one Main Hub
CREATE UNIQUE INDEX uq_hubs_single_main ON hubs (main) WHERE main;

CREATE TABLE dispatcher_hubs (
    user_id    UUID        PRIMARY KEY,
    hub_id     UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,

    -- Cross-module reference to identity.users without cascading deletes (ADR 0003 §5).
    CONSTRAINT fk_dispatcher_hubs_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_dispatcher_hubs_hub FOREIGN KEY (hub_id) REFERENCES hubs (id)
);

CREATE INDEX ix_dispatcher_hubs_hub ON dispatcher_hubs (hub_id);

INSERT INTO hubs (id, code, name, main, active, created_at) VALUES
    (gen_random_uuid(), 'LK-WP',  'Colombo Main Hub (Western Province)', TRUE,  TRUE, now()),
    (gen_random_uuid(), 'LK-CP',  'Central Province Hub',                FALSE, TRUE, now()),
    (gen_random_uuid(), 'LK-SP',  'Southern Province Hub',               FALSE, TRUE, now()),
    (gen_random_uuid(), 'LK-NP',  'Northern Province Hub',               FALSE, TRUE, now()),
    (gen_random_uuid(), 'LK-EP',  'Eastern Province Hub',                FALSE, TRUE, now()),
    (gen_random_uuid(), 'LK-NWP', 'North Western Province Hub',          FALSE, TRUE, now()),
    (gen_random_uuid(), 'LK-NCP', 'North Central Province Hub',          FALSE, TRUE, now()),
    (gen_random_uuid(), 'LK-UP',  'Uva Province Hub',                    FALSE, TRUE, now()),
    (gen_random_uuid(), 'LK-SGP', 'Sabaragamuwa Province Hub',           FALSE, TRUE, now());
