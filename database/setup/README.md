# database/setup/

DBA-run, pre-Flyway bootstrap scripts. These are **not** Flyway migrations — they must be executed once by a MySQL admin/root user **before** starting the backend so that Flyway can connect as `flyway_user`.

- `00_create_database_and_users.sql` — creates `edu_db` and the `flyway_user` (migration) and `app_user` (runtime) accounts.

After this script runs, Flyway migrations (run from the backend at startup) create the tables and grant `app_user` its privileges/roles.
