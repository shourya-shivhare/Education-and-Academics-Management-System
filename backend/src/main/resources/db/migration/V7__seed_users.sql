-- =============================================================================
-- V7__seed_users.sql
-- Creates the initial admin user at the database level.
--
-- DBMS concept: password_hash stores a BCrypt digest (cost=10), never plaintext.
-- BCrypt embeds a random salt in every hash so two hashes of the same password
-- are always different – safe to version-control in a migration.
--
-- The hash below is BCrypt(cost=10) of the string "admin123".
-- It was generated with Spring Security's BCryptPasswordEncoder:
--   new BCryptPasswordEncoder(10).encode("admin123")
--
-- Hash: $2a$10$N.uVJpRr9GJLPLs7fh69deZ3V18P9f7CYRPHMesBg9QGzlTGfNi8K
--
-- CHANGE the password immediately after first login.
-- For Faculty/Student/Registrar demo users, the DataInitializer bean
-- (edu.dbms.education.auth.DataInitializer) inserts them at startup
-- if the users table has only the admin row – avoids hard-coded hashes
-- for all four roles in SQL.
-- =============================================================================

INSERT IGNORE INTO users (username, password_hash, `role`, is_active, person_id)
VALUES (
    'admin',
    '$2a$10$N.uVJpRr9GJLPLs7fh69deZ3V18P9f7CYRPHMesBg9QGzlTGfNi8K',
    'Admin',
    TRUE,
    NULL
);
