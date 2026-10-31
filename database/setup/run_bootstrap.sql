-- Quick check: run this FIRST to verify MySQL is up and edu_db exists
-- mysql -u root -p < this_file.sql

SOURCE E:/Desktop/Web Development/Education-and-Academics-Management-System/database/setup/00_create_database_and_users.sql;

-- Verify
SELECT user, host FROM mysql.user WHERE user IN ('flyway_user','app_user');
SHOW DATABASES LIKE 'edu_db';
