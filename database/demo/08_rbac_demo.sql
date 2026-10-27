-- =============================================================================
-- 08_rbac_demo.sql
-- Role-Based Access Control (RBAC) Demonstration: Roles, Grants, & Revocations
-- Database: edu_db | Tool: MySQL Workbench / MySQL CLI (Run as root or admin)
--
-- DBMS Concept Demonstrated:
--   1. Principle of Least Privilege: users are granted ONLY the permissions
--      strictly required to fulfill their business functions.
--   2. MySQL 8 Role System: CREATE ROLE, GRANT privileges TO role,
--      GRANT role TO user, SET DEFAULT ROLE.
--   3. Strict Separation of Duties:
--      - Registrar: Student data, fee payments, admissions; NO user credentials or passwords.
--      - Faculty: Attendance, marks, teaching workload; NO fee data, NO student deletion.
--      - Student: Anonymized / projected views only (v_student_public, v_student_transcript).
--      - Column-Level Security: Restricting queries to specific columns on a relation.
--   4. Dynamic Privilege Revocation (REVOKE) and immediate permission enforcement.
-- =============================================================================

USE edu_db;

-- =============================================================================
-- PART 1: Create Concrete Test MySQL Database Users
-- (Simulates real-world database users connecting to MySQL)
-- =============================================================================
DROP USER IF EXISTS 'demo_registrar'@'localhost';
DROP USER IF EXISTS 'demo_faculty'@'localhost';
DROP USER IF EXISTS 'demo_student'@'localhost';
DROP USER IF EXISTS 'demo_advisor'@'localhost';

CREATE USER 'demo_registrar'@'localhost' IDENTIFIED BY 'Registrar#2024!';
CREATE USER 'demo_faculty'@'localhost'   IDENTIFIED BY 'Faculty#2024!';
CREATE USER 'demo_student'@'localhost'   IDENTIFIED BY 'Student#2024!';
CREATE USER 'demo_advisor'@'localhost'   IDENTIFIED BY 'Advisor#2024!';


-- =============================================================================
-- PART 2: Assign Predefined MySQL Roles (from V6__roles_and_grants.sql)
-- =============================================================================

-- Assign registrar_role
GRANT 'registrar_role' TO 'demo_registrar'@'localhost';
SET DEFAULT ROLE 'registrar_role' TO 'demo_registrar'@'localhost';

-- Assign faculty_role
GRANT 'faculty_role' TO 'demo_faculty'@'localhost';
SET DEFAULT ROLE 'faculty_role' TO 'demo_faculty'@'localhost';

-- Assign student_role
GRANT 'student_role' TO 'demo_student'@'localhost';
SET DEFAULT ROLE 'student_role' TO 'demo_student'@'localhost';

-- Assign column-level demo role
GRANT 'advisor_column_demo_role' TO 'demo_advisor'@'localhost';
SET DEFAULT ROLE 'advisor_column_demo_role' TO 'demo_advisor'@'localhost';

FLUSH PRIVILEGES;

-- Verification of Role Assignments:
SHOW GRANTS FOR 'demo_registrar'@'localhost';
SHOW GRANTS FOR 'demo_faculty'@'localhost';
SHOW GRANTS FOR 'demo_student'@'localhost';
SHOW GRANTS FOR 'demo_advisor'@'localhost';


-- =============================================================================
-- PART 3: Registrar Role Verification (demo_registrar)
-- Expected Behavior: Can manage student & fee tables; DENIED access to auth credentials.
-- =============================================================================

-- 3A: Permitted Action - Registrar reads student registry
-- Connect as: mysql -u demo_registrar -p'Registrar#2024!' edu_db
SELECT student_id, roll_no, program_id, current_semester, status 
FROM edu_db.student 
LIMIT 3;
-- Expected Output: SUCCESS (Returns 3 student rows)

-- 3B: Permitted Action - Registrar views fee payment transactions
SELECT payment_id, student_id, amount, payment_mode, status 
FROM edu_db.fee_payment 
LIMIT 3;
-- Expected Output: SUCCESS (Returns 3 fee payment records)

-- 3C: FORBIDDEN Action - Registrar attempts to read authentication credentials
SELECT user_id, username, password_hash, role 
FROM edu_db.users;
/*
Expected Output:
ERROR 1142 (42000): SELECT command denied to user 'demo_registrar'@'localhost' for table 'users'
Explanation: Registrar has zero access to the security/auth domain. Passwords and credentials remain isolated.
*/

-- 3D: FORBIDDEN Action - Registrar attempts DDL (schema destruction)
DROP TABLE edu_db.student;
/*
Expected Output:
ERROR 1142 (42000): DROP command denied to user 'demo_registrar'@'localhost' for table 'student'
Explanation: Schema modification is reserved exclusively for flyway_user.
*/


-- =============================================================================
-- PART 4: Faculty Role Verification (demo_faculty)
-- Expected Behavior: Can input attendance and marks; DENIED access to financial fees.
-- =============================================================================

-- 4A: Permitted Action - Faculty reads teaching workload view
-- Connect as: mysql -u demo_faculty -p'Faculty#2024!' edu_db
SELECT employee_no, first_name, offerings_count, total_enrolled_students 
FROM edu_db.v_faculty_workload 
LIMIT 3;
-- Expected Output: SUCCESS (Returns assigned workload statistics)

-- 4B: Permitted Action - Faculty updates attendance record
UPDATE edu_db.attendance 
SET status = 'Present' 
WHERE attendance_id = 1;
-- Expected Output: SUCCESS (Query OK, 1 row affected)

-- 4C: FORBIDDEN Action - Faculty attempts to read fee payments of students
SELECT * FROM edu_db.fee_payment;
/*
Expected Output:
ERROR 1142 (42000): SELECT command denied to user 'demo_faculty'@'localhost' for table 'fee_payment'
Explanation: Faculty members have no legitimate business requirement to inspect student fee records.
*/

-- 4D: FORBIDDEN Action - Faculty attempts to delete a student record
DELETE FROM edu_db.student WHERE student_id = 1;
/*
Expected Output:
ERROR 1142 (42000): DELETE command denied to user 'demo_faculty'@'localhost' for table 'student'
*/


-- =============================================================================
-- PART 5: Student Role Verification (demo_student)
-- Expected Behavior: Can only query projected security views; direct base table access DENIED.
-- =============================================================================

-- 5A: Permitted Action - Student queries public directory view (PII masked)
-- Connect as: mysql -u demo_student -p'Student#2024!' edu_db
SELECT student_id, roll_no, first_name, program_name, department_name 
FROM edu_db.v_student_public 
LIMIT 3;
-- Expected Output: SUCCESS (Returns directory without PII)

-- 5B: Permitted Action - Student queries official transcript view
SELECT roll_no, student_name, subject_code, grade, grade_point 
FROM edu_db.v_student_transcript 
LIMIT 3;
-- Expected Output: SUCCESS

-- 5C: FORBIDDEN Action - Student attempts to access raw Person base table (PII / Address / DOB)
SELECT person_id, first_name, date_of_birth, address_line, pin_code 
FROM edu_db.person;
/*
Expected Output:
ERROR 1142 (42000): SELECT command denied to user 'demo_student'@'localhost' for table 'person'
Explanation: Base table access is completely prohibited; data must be accessed via v_student_public.
*/

-- 5D: FORBIDDEN Action - Student attempts to tamper with marks
UPDATE edu_db.marks SET marks_obtained = 100.00 WHERE marks_id = 1;
/*
Expected Output:
ERROR 1142 (42000): UPDATE command denied to user 'demo_student'@'localhost' for table 'marks'
*/


-- =============================================================================
-- PART 6: Column-Level Security Demonstration (demo_advisor)
-- Role: advisor_column_demo_role
-- Column Grants: SELECT (student_id, roll_no, program_id) ON student
--                SELECT (person_id, first_name, last_name, email) ON person
-- =============================================================================

-- 6A: Permitted Action - Query ONLY granted columns
-- Connect as: mysql -u demo_advisor -p'Advisor#2024!' edu_db
SELECT student_id, roll_no, program_id 
FROM edu_db.student 
LIMIT 3;
-- Expected Output: SUCCESS

-- 6B: FORBIDDEN Action - Querying even ONE ungranted column on the same table
SELECT student_id, roll_no, admission_date 
FROM edu_db.student;
/*
Expected Output:
ERROR 1143 (42000): SELECT command denied to user 'demo_advisor'@'localhost' for column 'admission_date' in table 'student'
Explanation: MySQL enforces column-level ACLs. Even though the user has table-level interest,
             unauthorized columns cause the entire statement to fail.
*/


-- =============================================================================
-- PART 7: Dynamic Privilege Revocation (REVOKE) & Real-Time Enforcement
-- =============================================================================
-- Run as admin / root:

-- 7A: Revoke UPDATE on marks from faculty_role
REVOKE UPDATE ON edu_db.marks FROM 'faculty_role';

-- 7B: Verify that demo_faculty can no longer update marks
-- Attempt as demo_faculty:
-- UPDATE edu_db.marks SET marks_obtained = 85.00 WHERE marks_id = 1;
/*
Expected Output:
ERROR 1142 (42000): UPDATE command denied to user 'demo_faculty'@'localhost' for table 'marks'
Explanation: MySQL role privilege changes take effect immediately on active role holders.
*/

-- 7C: Restore original privilege
GRANT UPDATE ON edu_db.marks TO 'faculty_role';

-- 7D: Revoke entire role from a user
REVOKE 'registrar_role' FROM 'demo_registrar'@'localhost';
SHOW GRANTS FOR 'demo_registrar'@'localhost';
/*
Expected Output:
+-------------------------------------------------------------+
| Grants for demo_registrar@localhost                         |
+-------------------------------------------------------------+
| GRANT USAGE ON *.* TO `demo_registrar`@`localhost`          |
+-------------------------------------------------------------+
Explanation: Stripping the role removes all underlying table permissions in one atomic action.
*/

-- Re-assign role to leave system in clean state:
GRANT 'registrar_role' TO 'demo_registrar'@'localhost';


-- =============================================================================
-- PART 8: Optional Cleanup (Leave commented out to allow manual testing)
-- =============================================================================
-- DROP USER IF EXISTS 'demo_registrar'@'localhost';
-- DROP USER IF EXISTS 'demo_faculty'@'localhost';
-- DROP USER IF EXISTS 'demo_student'@'localhost';
-- DROP USER IF EXISTS 'demo_advisor'@'localhost';
