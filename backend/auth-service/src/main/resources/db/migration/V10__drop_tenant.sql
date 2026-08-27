-- Tenant was demoted to an optional sub-scope of Product in Phase 1 of the
-- multi-product identity platform redesign and never actually used for real
-- behavior since -- see docs/design/multi-product-identity-platform.md
-- section 3. Removed entirely: Product is the real tenancy boundary.
--
-- The FK constraint's name is looked up dynamically rather than hardcoded:
-- confirmed live that it differs across environments (V1's baseline SQL,
-- a cleaned pg_dump snapshot, documents it as "fk_app_user_tenant", but a
-- database that predates Flyway's adoption and had this constraint created
-- by Hibernate's ddl-auto has it under Hibernate's own generated name
-- instead, e.g. "fk50gh2j4plq5le4eixrc14x6n1") -- trusting the documented
-- name outright broke this migration on exactly that kind of database.

DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT conname INTO constraint_name
    FROM pg_constraint
    WHERE conrelid = 'app_user'::regclass
      AND contype = 'f'
      AND confrelid = 'tenant'::regclass;

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE app_user DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

ALTER TABLE app_user DROP COLUMN tenant_code;
DROP TABLE tenant;
