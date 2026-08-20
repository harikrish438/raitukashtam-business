-- product_membership.identity_id and tenant.product_id were left nullable
-- in Phases 1-2 purely as a pre-Flyway `ddl-auto: update` workaround; that
-- is not their real intent. Safe to tighten now that ProductDataSeeder,
-- registration, and Google auto-provisioning all always populate both --
-- this statement itself fails loudly if that assumption is wrong.

ALTER TABLE product_membership ALTER COLUMN identity_id SET NOT NULL;
ALTER TABLE tenant ALTER COLUMN product_id SET NOT NULL;
