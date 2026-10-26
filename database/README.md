# database/

SQL assets for the Education and Academics Management System. The MySQL 8 (InnoDB) database is the source of truth — business rules live here as constraints, triggers, stored procedures, views, and roles.

- `setup/`  — DBA-run, pre-Flyway bootstrap scripts (create database + users). Run BEFORE Flyway.
- `seed/`   — seed data SQL (populated in a later step).
- `queries/`— reference / example SQL queries (populated in a later step).
- `demo/`   — contains the single allowed SQL-injection demonstration file (created in a later step).

Flyway migrations (executed by `flyway_user`) create tables and grant `app_user` privileges/roles. They live under `backend/src/main/resources/db/migration/`.
