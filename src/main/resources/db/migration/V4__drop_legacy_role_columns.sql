-- Drops the columns superseded by V3's role/role_assignment tables, now
-- that the backfill has run and the Java code no longer maps them.

ALTER TABLE app_user DROP CONSTRAINT app_user_role_check;
ALTER TABLE app_user DROP COLUMN role;

ALTER TABLE refresh_token DROP COLUMN role;
