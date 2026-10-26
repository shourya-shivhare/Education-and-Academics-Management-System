-- =============================================================================
-- 00_create_database_and_users.sql
-- =============================================================================
-- DBA-run, PRE-FLYWAY bootstrap script. This is NOT a Flyway migration.
--
-- It MUST be run by a MySQL admin / root user BEFORE starting the backend so
-- that Flyway can connect as flyway_user and run the migrations.
--
--   mysql -u root -p < database/setup/00_create_database_and_users.sql
--
-- After this script runs, Flyway migrations (executed by flyway_user at
-- backend startup) create the tables, triggers, stored procedures, views,
-- and grant app_user its specific privileges and role memberships.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Database (utf8mb4 for full Unicode support)
-- -----------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS edu_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 2. Migration user: flyway_user
--    Runs all Flyway migrations (DDL + DML) on edu_db and provisions app_user.
-- -----------------------------------------------------------------------------
-- NOTE: Placeholder password — MUST be changed before any real use. Source it
-- from a local secrets file / environment variable instead of hard-coding.
CREATE USER IF NOT EXISTS 'flyway_user'@'%' IDENTIFIED BY 'ChangeMe_flyway_2026!';

-- Full DDL + DML on edu_db. WITH GRANT OPTION lets migrations GRANT specific
-- privileges to app_user (migrations own the runtime-user provisioning).
GRANT ALL PRIVILEGES ON edu_db.* TO 'flyway_user'@'%' WITH GRANT OPTION;

-- Global static privileges to CREATE ROLE and DROP ROLE on *.* are needed
-- because the project models access control with MySQL ROLES (business rules
-- in the DB). Migrations create roles and GRANT them to app_user; role
-- creation/drop requires a global static privilege, not a per-db one.
GRANT CREATE ROLE, DROP ROLE ON *.* TO 'flyway_user'@'%';

-- -----------------------------------------------------------------------------
-- 3. Runtime user: app_user
--    Used by the Spring Boot backend at runtime to call procs/queries.
--    NO DDL rights here. Do NOT grant table/DDL privileges in this file.
-- -----------------------------------------------------------------------------
-- NOTE: Placeholder password — MUST be changed before any real use. Source it
-- from a local secrets file / environment variable instead of hard-coding.
CREATE USER IF NOT EXISTS 'app_user'@'%' IDENTIFIED BY 'ChangeMe_app_2026!';

-- USAGE is the explicit no-privilege marker. Flyway migrations will GRANT the
-- specific SELECT/INSERT/UPDATE/DELETE/EXECUTE privileges and role memberships
-- that app_user actually needs (least privilege, defined per migration).
GRANT USAGE ON edu_db.* TO 'app_user'@'%';

-- -----------------------------------------------------------------------------
-- Notes
-- -----------------------------------------------------------------------------
-- Authentication: caching_sha2_password (MySQL 8 default). The JDBC connector
-- must support it (mysql-connector-j does). Replace the placeholder passwords
-- above before any real use.
--
-- Host: '%' is used for dev convenience. In production, restrict these users
-- to specific hosts (e.g. 'app_user'@'10.0.0.%' or 'localhost').
--
-- Idempotent: CREATE USER/GRANT use IF NOT EXISTS / are safe to re-run.
-- No FLUSH PRIVILEGES needed (not required with CREATE USER / GRANT).
--
-- Summary:
--   flyway_user — runs migrations (DDL+DML on edu_db), provisions app_user's
--                 grants/roles (WITH GRANT OPTION + CREATE/DROP ROLE globally).
--   app_user     — runtime only; starts with USAGE and receives its specific
--                 privileges/roles from Flyway migrations.
-- =============================================================================
