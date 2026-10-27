-- =============================================================================
-- 09_ddl_alter_drop_truncate.sql
-- Data Definition Language (DDL) Demonstration: ALTER, RENAME, TRUNCATE, & DROP
-- Database: edu_db | Tool: MySQL Workbench / MySQL CLI
--
-- DBMS Concept Demonstrated:
--   1. DDL vs. DML: DDL changes schema metadata in the MySQL 8 data dictionary.
--   2. Implicit Commit: Unlike DML, MySQL DDL statements cannot be rolled back
--      (they issue an automatic implicit commit before and after execution).
--   3. Schema Evolution (ALTER TABLE):
--      - ADD COLUMN, MODIFY COLUMN, DROP COLUMN, ADD CONSTRAINT.
--   4. RENAME TABLE: Atomic metadata rename without moving physical data.
--   5. TRUNCATE TABLE vs. DELETE:
--      - TRUNCATE: DDL operation, drops and recreates storage pages (fast, resets
--        AUTO_INCREMENT, triggers do NOT fire, cannot be rolled back).
--      - DELETE: DML operation, logs row-by-row undo/redo, triggers fire.
--   6. Referential Integrity Protection on DROP:
--      - Foreign keys prevent dropping parent tables while child tables depend on them.
--   7. Destructive Isolation: Kept strictly in demo scripts; never in Flyway migrations.
-- =============================================================================

USE edu_db;

-- =============================================================================
-- PART 1: Create an Isolated Sandbox Table for DDL Testing
-- =============================================================================
DROP TABLE IF EXISTS demo_student_archive;
DROP TABLE IF EXISTS demo_alumni_registry;

CREATE TABLE demo_student_archive (
    archive_id      INT AUTO_INCREMENT PRIMARY KEY,
    student_id      INT NOT NULL,
    roll_no         VARCHAR(20) NOT NULL UNIQUE,
    degree_awarded  VARCHAR(50) NOT NULL,
    cgpa            DECIMAL(3, 2) NOT NULL,
    archive_status  ENUM('Pending', 'Verified', 'Archived') DEFAULT 'Pending',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_archive_cgpa CHECK (cgpa >= 0.00 AND cgpa <= 10.00)
) ENGINE=InnoDB;

-- Populate with initial sample data
INSERT INTO demo_student_archive (student_id, roll_no, degree_awarded, cgpa, archive_status)
VALUES 
    (101, 'ARCH-2024-001', 'B.Tech Computer Science', 8.95, 'Verified'),
    (102, 'ARCH-2024-002', 'B.Tech Electrical Eng',   7.60, 'Pending'),
    (103, 'ARCH-2024-003', 'M.Tech Data Science',      9.20, 'Archived');

SELECT 'Initial Data' AS stage, archive_id, roll_no, degree_awarded, cgpa, archive_status 
FROM demo_student_archive;


-- =============================================================================
-- PART 2: ALTER TABLE Demonstrations
-- =============================================================================

-- 2A: ADD COLUMN
-- Add a graduation date column and an optional remarks text field
ALTER TABLE demo_student_archive
    ADD COLUMN graduation_date DATE NULL AFTER degree_awarded,
    ADD COLUMN remarks TEXT NULL;

-- Verify columns were added:
DESCRIBE demo_student_archive;
/*
Expected Output:
+-----------------+----------------------------------------+------+-----+-------------------+-------------------+
| Field           | Type                                   | Null | Key | Default           | Extra             |
+-----------------+----------------------------------------+------+-----+-------------------+-------------------+
| archive_id      | int                                    | NO   | PRI | NULL              | auto_increment    |
| student_id      | int                                    | NO   |     | NULL              |                   |
| roll_no         | varchar(20)                            | NO   | UNI | NULL              |                   |
| degree_awarded  | varchar(50)                            | NO   |     | NULL              |                   |
| graduation_date | date                                   | YES  |     | NULL              |                   |
| remarks         | text                                   | YES  |     | NULL              |                   |
| cgpa            | decimal(3,2)                           | NO   |     | NULL              |                   |
| archive_status  | enum('Pending','Verified','Archived')  | YES  |     | Pending           |                   |
| created_at      | timestamp                              | YES  |     | CURRENT_TIMESTAMP | DEFAULT_GENERATED |
+-----------------+----------------------------------------+------+-----+-------------------+-------------------+
*/

-- 2B: MODIFY COLUMN
-- Widen degree_awarded from VARCHAR(50) to VARCHAR(100) and change remarks default
ALTER TABLE demo_student_archive
    MODIFY COLUMN degree_awarded VARCHAR(100) NOT NULL,
    MODIFY COLUMN remarks VARCHAR(255) NOT NULL DEFAULT 'Standard archival record';

-- 2C: ADD CHECK CONSTRAINT
-- Enforce graduation_date must be on or after institutional founding date (2010-01-01)
ALTER TABLE demo_student_archive
    ADD CONSTRAINT chk_graduation_date CHECK (graduation_date IS NULL OR graduation_date >= '2010-01-01');

-- Test constraint enforcement:
-- Attempt invalid graduation date before 2010:
-- INSERT INTO demo_student_archive (student_id, roll_no, degree_awarded, graduation_date, cgpa)
-- VALUES (104, 'ARCH-FAIL-001', 'B.Tech IT', '1995-05-15', 7.50);
/*
Expected Output:
ERROR 3819 (HY000): Check constraint 'chk_graduation_date' is violated.
*/

-- 2D: DROP COLUMN
-- Remove redundant remarks column
ALTER TABLE demo_student_archive
    DROP COLUMN remarks;


-- =============================================================================
-- PART 3: RENAME TABLE Demonstration
-- =============================================================================
-- Atomically rename the table to reflect updated institutional terminology
RENAME TABLE demo_student_archive TO demo_alumni_registry;

-- Verify new name exists and old name does not:
SELECT table_name, engine, table_rows 
FROM information_schema.tables 
WHERE table_schema = 'edu_db' AND table_name IN ('demo_student_archive', 'demo_alumni_registry');
/*
Expected Output:
+----------------------+--------+------------+
| TABLE_NAME           | ENGINE | TABLE_ROWS |
+----------------------+--------+------------+
| demo_alumni_registry | InnoDB |          3 |
+----------------------+--------+------------+
*/


-- =============================================================================
-- PART 4: TRUNCATE TABLE vs. DELETE Demonstration
-- =============================================================================

-- Insert additional record to observe auto_increment behavior
INSERT INTO demo_alumni_registry (student_id, roll_no, degree_awarded, cgpa)
VALUES (104, 'ARCH-2024-004', 'Ph.D. Computer Science', 9.85);

SELECT 'Before TRUNCATE' AS stage, archive_id, roll_no FROM demo_alumni_registry;
-- Notice max archive_id is 4.

-- Execute TRUNCATE:
TRUNCATE TABLE demo_alumni_registry;

SELECT 'After TRUNCATE' AS stage, COUNT(*) AS remaining_rows FROM demo_alumni_registry;
-- Expected Output: remaining_rows = 0

-- Insert new row after TRUNCATE to prove AUTO_INCREMENT counter was reset to 1
INSERT INTO demo_alumni_registry (student_id, roll_no, degree_awarded, cgpa)
VALUES (105, 'ARCH-2025-001', 'B.Tech Mechanical Eng', 8.20);

SELECT 'Auto-Increment Reset Proof' AS check_type, archive_id, roll_no 
FROM demo_alumni_registry;
/*
Expected Output:
+----------------------------+------------+---------------+
| check_type                 | archive_id | roll_no       |
+----------------------------+------------+---------------+
| Auto-Increment Reset Proof |          1 | ARCH-2025-001 |
+----------------------------+------------+---------------+
Explanation:
TRUNCATE resets the AUTO_INCREMENT sequence back to 1.
In contrast, DELETE FROM would have left the internal counter at 5!
*/


-- =============================================================================
-- PART 5: Referential Integrity Protection Against Destructive DROP
-- =============================================================================

-- Attempt to DROP the parent table 'department' while 'program' and 'student' reference it:
-- DROP TABLE department;
/*
Expected Output:
ERROR 3730 (HY000): Cannot drop table 'department' referenced by a foreign key constraint 'fk_program_department' on table 'program'.
Explanation:
InnoDB enforces strict referential integrity. Parent tables cannot be dropped
while active foreign key references exist in child tables.
*/

-- Safe DROP of our isolated demo table:
DROP TABLE IF EXISTS demo_alumni_registry;

-- Verification of successful drop:
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'edu_db' AND table_name = 'demo_alumni_registry';
-- Expected Output: Empty set (Table completely unlinked from MySQL data dictionary)
