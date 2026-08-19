-- app_user.password was made vestigial in Phase 2 of the multi-product
-- identity platform redesign: password verification moved to
-- identity_credential.password_hash. Kept mapped-but-unused through Phase 2
-- only so the one-time backfill could read the old BCrypt hash out of it;
-- that backfill has fully run. Safe to drop for good.

ALTER TABLE app_user DROP COLUMN password;
