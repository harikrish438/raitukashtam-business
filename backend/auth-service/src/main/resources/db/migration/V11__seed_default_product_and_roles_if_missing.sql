-- V3 seeds the 5 legacy role codes with `FROM product p ... WHERE p.code =
-- 'RAITUKASHTAM'` -- but on a genuinely fresh database, that runs before
-- ProductDataSeeder (a CommandLineRunner, which always runs after every
-- Flyway migration completes) has created the RAITUKASHTAM row, so the join
-- matches zero rows and V3 silently seeds no roles at all. Confirmed live:
-- a fresh test-profile boot lets a user register right up until
-- RoleService.assignDefaultRole(), which then 404s with "Default role
-- 'CONSUMER' not found for product: RAITUKASHTAM".
--
-- V3 itself cannot be edited -- Flyway validates the checksum of every
-- already-applied migration, and V3 already ran with its original content
-- on every environment that predates this fix (including dev). Fix here
-- instead: ensure the product exists, then re-run V3's exact role-seeding
-- logic, both guarded so this is a genuine no-op wherever V3 already
-- succeeded (i.e. every existing environment).

INSERT INTO product (created_at, created_by, code, name, status)
SELECT now(), 'system-migration-v11', 'RAITUKASHTAM', 'Raitukashtam', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM product WHERE code = 'RAITUKASHTAM');

INSERT INTO role (created_at, created_by, product_id, code, name)
SELECT now(), 'system-migration-v11', p.id, codes.code, codes.name
FROM product p
CROSS JOIN (VALUES
    ('ADMIN', 'Admin'),
    ('FARMER', 'Farmer'),
    ('BUYER', 'Buyer'),
    ('DELIVERY_PARTNER', 'Delivery Partner'),
    ('CONSUMER', 'Consumer')
) AS codes(code, name)
WHERE p.code = 'RAITUKASHTAM'
  AND NOT EXISTS (
      SELECT 1 FROM role r WHERE r.product_id = p.id AND r.code = codes.code
  );
