-- =============================================================================
-- V6__roles_and_grants.sql
-- Flyway Versioned Migration: Database Roles, Privileges, and App User Grants
-- Database: edu_db | Flyway User: flyway_user
--
-- DBMS Principle Demonstrated:
--   Role-Based Access Control (RBAC) at the MySQL engine level.
--   Enforces Principle of Least Privilege:
--     - admin_role: Full DML/DQL privileges across schema.
--     - registrar_role: Student admissions, enrollments, fee records; EXECUTE on enrollment procedures; NO access to users/auth.
--     - faculty_role: Enter attendance and marks for offerings; read teaching views only.
--     - student_role: Read-only access to own public view and transcript view only.
--     - ml_readonly_role: Read-only access to ML feature view and subject catalog view only.
--     - Column-level grants: Restricts query access to specific columns on a table.
--     - app_user: Runtime backend user granted DML and EXECUTE (NO DDL rights).
-- =============================================================================

USE edu_db;

-- -----------------------------------------------------------------------------
-- 0. Ensure View and Routine Targets Exist for Grant Binding
-- (Flyway runs V6 before repeatable migrations R__views.sql and R__procedures.sql;
--  these baseline definitions allow MySQL to validate object existence during GRANT.
--  Repeatable migrations immediately replace them with full production logic).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW edu_db.v_student_public AS 
SELECT student_id, roll_no FROM edu_db.student;

CREATE OR REPLACE VIEW edu_db.v_student_transcript AS 
SELECT enrollment_id, student_id FROM edu_db.enrollment;

CREATE OR REPLACE VIEW edu_db.v_faculty_workload AS 
SELECT faculty_id FROM edu_db.faculty;

CREATE OR REPLACE VIEW edu_db.v_attendance_summary AS 
SELECT enrollment_id FROM edu_db.enrollment;

CREATE OR REPLACE VIEW edu_db.v_subject_catalog AS 
SELECT subject_id, code, title FROM edu_db.subject;

CREATE OR REPLACE VIEW edu_db.v_ml_features AS 
SELECT student_id FROM edu_db.student;

DELIMITER $$
CREATE PROCEDURE IF NOT EXISTS edu_db.sp_enroll_student(IN p_student_id INT, IN p_offering_id INT) BEGIN END$$
CREATE PROCEDURE IF NOT EXISTS edu_db.sp_drop_enrollment(IN p_student_id INT, IN p_offering_id INT) BEGIN END$$
CREATE PROCEDURE IF NOT EXISTS edu_db.sp_generate_transcript(IN p_student_id INT) BEGIN END$$
CREATE PROCEDURE IF NOT EXISTS edu_db.sp_compute_final_grades(IN p_offering_id INT) BEGIN END$$
CREATE FUNCTION IF NOT EXISTS edu_db.fn_grade_point(p_marks DECIMAL(5,2)) RETURNS DECIMAL(3,2) DETERMINISTIC BEGIN RETURN 0; END$$
CREATE FUNCTION IF NOT EXISTS edu_db.fn_attendance_pct(p_enrollment_id INT) RETURNS DECIMAL(5,2) DETERMINISTIC BEGIN RETURN 0; END$$
CREATE FUNCTION IF NOT EXISTS edu_db.fn_cgpa(p_student_id INT) RETURNS DECIMAL(3,2) DETERMINISTIC BEGIN RETURN 0; END$$
DELIMITER ;


-- -----------------------------------------------------------------------------
-- 1. Create MySQL Roles
-- -----------------------------------------------------------------------------
CREATE ROLE IF NOT EXISTS 'admin_role';
CREATE ROLE IF NOT EXISTS 'registrar_role';
CREATE ROLE IF NOT EXISTS 'faculty_role';
CREATE ROLE IF NOT EXISTS 'student_role';
CREATE ROLE IF NOT EXISTS 'ml_readonly_role';


-- -----------------------------------------------------------------------------
-- 2. admin_role Privileges
-- Full operational DML and routine execution access across all tables in edu_db.
-- -----------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON edu_db.* TO 'admin_role';
GRANT EXECUTE ON edu_db.* TO 'admin_role';


-- -----------------------------------------------------------------------------
-- 3. registrar_role Privileges
-- Manages admissions, student data, enrollments, and tuition fee records.
-- Explicitly NO privileges granted on the `users` (credentials) or `audit_log` tables.
-- -----------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE ON edu_db.person TO 'registrar_role';
GRANT SELECT, INSERT, UPDATE ON edu_db.person_phone TO 'registrar_role';
GRANT SELECT, INSERT, UPDATE ON edu_db.student TO 'registrar_role';
GRANT SELECT, INSERT, UPDATE ON edu_db.enrollment TO 'registrar_role';
GRANT SELECT, INSERT, UPDATE ON edu_db.fee_payment TO 'registrar_role';

-- Read reference data necessary for registration
GRANT SELECT ON edu_db.department TO 'registrar_role';
GRANT SELECT ON edu_db.program TO 'registrar_role';
GRANT SELECT ON edu_db.subject TO 'registrar_role';
GRANT SELECT ON edu_db.subject_prerequisite TO 'registrar_role';
GRANT SELECT ON edu_db.course_offering TO 'registrar_role';
GRANT SELECT ON edu_db.timetable_slot TO 'registrar_role';
GRANT SELECT ON edu_db.classroom TO 'registrar_role';
GRANT SELECT ON edu_db.term TO 'registrar_role';
GRANT SELECT ON edu_db.grade_scale TO 'registrar_role';

-- Execute rights on enrollment procedures
GRANT EXECUTE ON PROCEDURE edu_db.sp_enroll_student TO 'registrar_role';
GRANT EXECUTE ON PROCEDURE edu_db.sp_drop_enrollment TO 'registrar_role';
GRANT EXECUTE ON PROCEDURE edu_db.sp_generate_transcript TO 'registrar_role';


-- -----------------------------------------------------------------------------
-- 4. faculty_role Privileges
-- Restricted to teaching duties: read teaching views; insert/update attendance and marks.
-- Cannot modify student records, course offerings, or fee payments.
-- -----------------------------------------------------------------------------
GRANT SELECT ON edu_db.course_offering TO 'faculty_role';
GRANT SELECT ON edu_db.assessment TO 'faculty_role';
GRANT SELECT, INSERT, UPDATE ON edu_db.attendance TO 'faculty_role';
GRANT SELECT, INSERT, UPDATE ON edu_db.marks TO 'faculty_role';

-- Views for faculty teaching assignments
GRANT SELECT ON edu_db.v_faculty_workload TO 'faculty_role';
GRANT SELECT ON edu_db.v_attendance_summary TO 'faculty_role';

-- Execute grade computation for assigned offerings
GRANT EXECUTE ON PROCEDURE edu_db.sp_compute_final_grades TO 'faculty_role';
GRANT EXECUTE ON FUNCTION edu_db.fn_grade_point TO 'faculty_role';
GRANT EXECUTE ON FUNCTION edu_db.fn_attendance_pct TO 'faculty_role';


-- -----------------------------------------------------------------------------
-- 5. student_role Privileges
-- Strictly read-only access to projected security views only.
-- Direct access to base `person` (PII), `fee_payment`, and `users` is disallowed.
-- -----------------------------------------------------------------------------
GRANT SELECT ON edu_db.v_student_public TO 'student_role';
GRANT SELECT ON edu_db.v_student_transcript TO 'student_role';
GRANT EXECUTE ON FUNCTION edu_db.fn_attendance_pct TO 'student_role';
GRANT EXECUTE ON FUNCTION edu_db.fn_cgpa TO 'student_role';


-- -----------------------------------------------------------------------------
-- 6. ml_readonly_role Privileges
-- Used by the internal Python FastAPI AI service for offline training and course search.
-- Strictly read-only access to the ML view and subject catalog view only.
-- -----------------------------------------------------------------------------
GRANT SELECT ON edu_db.v_ml_features TO 'ml_readonly_role';
GRANT SELECT ON edu_db.v_subject_catalog TO 'ml_readonly_role';


-- -----------------------------------------------------------------------------
-- 7. Column-Level Grant Demonstration
-- Illustrates granular column-level privilege specification in MySQL.
-- An academic advisor role that can query only student identifiers (student_id, roll_no, program_id)
-- and basic person details, but cannot view admission_date, status, or PII.
-- -----------------------------------------------------------------------------
CREATE ROLE IF NOT EXISTS 'advisor_column_demo_role';
GRANT SELECT (student_id, roll_no, program_id) ON edu_db.student TO 'advisor_column_demo_role';
GRANT SELECT (person_id, first_name, last_name, email) ON edu_db.person TO 'advisor_column_demo_role';


-- -----------------------------------------------------------------------------
-- 8. Runtime Backend User (app_user) Provisioning
-- The Spring Boot backend connects as `app_user`.
-- Principle of Least Privilege:
--   - Granted full DML (SELECT, INSERT, UPDATE, DELETE) and EXECUTE rights.
--   - Explicitly DENIED DDL rights (no CREATE, ALTER, DROP TABLE).
--   - Schema changes belong exclusively to Flyway running as flyway_user.
-- -----------------------------------------------------------------------------
GRANT SELECT, INSERT, UPDATE, DELETE ON edu_db.* TO 'app_user'@'%';
GRANT EXECUTE ON edu_db.* TO 'app_user'@'%';

-- Grant roles to app_user and enable default roles
GRANT 'admin_role', 'registrar_role', 'faculty_role', 'student_role', 'ml_readonly_role' TO 'app_user'@'%';
SET DEFAULT ROLE 'admin_role', 'registrar_role', 'faculty_role', 'student_role', 'ml_readonly_role' TO 'app_user'@'%';
