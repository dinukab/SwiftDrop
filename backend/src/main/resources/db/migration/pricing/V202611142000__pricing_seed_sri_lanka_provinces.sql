-- Owner: pricing module (ADR 0003). One delivery zone per province of Sri Lanka, and a pricing rule
-- for every ordered pair of provinces so any pickup/drop-off combination can be quoted.
--
-- The fee amounts are PLACEHOLDERS, not agreed rates. Replace them through the pricing admin
-- (sub-parts 8.4–8.7 in docs/PROGRESS.md) or a follow-up migration once the tariff is confirmed.
--   same province:      base 150.00 LKR, 100.00 LKR per kg
--   different province: base 300.00 LKR, 100.00 LKR per kg
--   size surcharges:    small 0.00, medium 150.00, large 350.00 LKR

INSERT INTO zones (id, code, name, active, version, created_at) VALUES
    (gen_random_uuid(), 'LK-WP',  'Western Province',        TRUE, 0, now()),
    (gen_random_uuid(), 'LK-CP',  'Central Province',        TRUE, 0, now()),
    (gen_random_uuid(), 'LK-SP',  'Southern Province',       TRUE, 0, now()),
    (gen_random_uuid(), 'LK-NP',  'Northern Province',       TRUE, 0, now()),
    (gen_random_uuid(), 'LK-EP',  'Eastern Province',        TRUE, 0, now()),
    (gen_random_uuid(), 'LK-NWP', 'North Western Province',  TRUE, 0, now()),
    (gen_random_uuid(), 'LK-NCP', 'North Central Province',  TRUE, 0, now()),
    (gen_random_uuid(), 'LK-UP',  'Uva Province',            TRUE, 0, now()),
    (gen_random_uuid(), 'LK-SGP', 'Sabaragamuwa Province',   TRUE, 0, now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO pricing_rules (
    id, origin_zone_id, destination_zone_id, base_fee, per_kg_fee,
    small_size_surcharge, medium_size_surcharge, large_size_surcharge,
    currency, active, version, created_at
)
SELECT
    gen_random_uuid(),
    origin.id,
    destination.id,
    CASE WHEN origin.id = destination.id THEN 150.00 ELSE 300.00 END,
    100.00,
    0.00,
    150.00,
    350.00,
    'LKR',
    TRUE,
    0,
    now()
FROM zones origin
CROSS JOIN zones destination
WHERE origin.code LIKE 'LK-%'
  AND destination.code LIKE 'LK-%'
ON CONFLICT ON CONSTRAINT uq_pricing_rules_origin_destination DO NOTHING;
