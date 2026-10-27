-- =============================================================================
-- 02_acid_consistency.sql
-- ACID Property Demonstration: CONSISTENCY (Integrity Constraints & Invariants)
-- Database: edu_db | Tool: MySQL Workbench / MySQL CLI
--
-- DBMS Concept:
-- Consistency ensures the database never enters an invalid state. Any transaction
-- that violates database invariants (Primary Keys, Foreign Keys, CHECK constraints,
-- UNIQUE constraints, or Trigger-enforced business rules) is rejected by the DBMS,
-- preserving relational schema integrity.
-- =============================================================================

-- =============================================================================
-- TEST 1: Domain / CHECK Constraint Violation (Negative Credits or Marks)
-- Invariant: chk_subject_credits CHECK (credits BETWEEN 1 AND 6)
-- =============================================================================
-- Attempting to insert a subject with 8 credits (exceeds allowed limit of 6):
INSERT INTO subject (code, title, credits, department_id, semester)
VALUES ('CS999', 'Invalid Super Course', 8, 1, 1);

-- Expected Output:
-- ERROR 3819 (HY000): Check constraint 'chk_subject_credits' is violated.


-- =============================================================================
-- TEST 2: Entity Integrity / UNIQUE Constraint Violation (Duplicate Enrollment)
-- Invariant: uq_enrollment_student_offering UNIQUE (student_id, offering_id)
-- =============================================================================
-- Student #31 is already enrolled in Offering #1 from seed data.
-- Attempting to insert a duplicate enrollment for the exact same student and offering:
INSERT INTO enrollment (student_id, offering_id, enroll_date, status)
VALUES (31, 1, CURDATE(), 'Enrolled');

-- Expected Output:
-- ERROR 1062 (23000): Duplicate entry '31-1' for key 'enrollment.uq_enrollment_student_offering'


-- =============================================================================
-- TEST 3: Referential Integrity Violation (Orphan Foreign Key)
-- Invariant: fk_enrollment_student FOREIGN KEY (student_id) REFERENCES student(student_id)
-- =============================================================================
-- Attempting to register an enrollment for a non-existent student (student_id = 99999):
INSERT INTO enrollment (student_id, offering_id, enroll_date, status)
VALUES (99999, 1, CURDATE(), 'Enrolled');

-- Expected Output:
-- ERROR 1452 (23000): Cannot add or update a child row: a foreign key constraint fails
-- (`edu_db`.`enrollment`, CONSTRAINT `fk_enrollment_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`student_id`))


-- =============================================================================
-- TEST 4: Self-Reference / DAG Check Constraint Violation
-- Invariant: chk_subject_prerequisite_selfref CHECK (subject_id <> prereq_subject_id)
-- =============================================================================
-- A subject cannot be its own prerequisite (prevents immediate self-cycles):
INSERT INTO subject_prerequisite (subject_id, prereq_subject_id, min_grade)
VALUES (1, 1, 'P');

-- Expected Output:
-- ERROR 3819 (HY000): Check constraint 'chk_subject_prerequisite_selfref' is violated.


-- =============================================================================
-- TEST 5: Procedural Trigger Invariant (Assessment Marks > Max Marks)
-- Invariant: trg_marks_before_insert SIGNAL SQLSTATE '45000'
-- =============================================================================
-- Internal Assessment #1 has max_marks = 50.
-- Attempting to award 55.0 marks:
INSERT INTO marks (enrollment_id, offering_id, assessment_no, marks_obtained, version)
VALUES (1, 1, 1, 55.0, 1);

-- Expected Output:
-- ERROR 1644 (45000): Invalid marks: Marks obtained cannot exceed the assessment maximum marks.


-- =============================================================================
-- Consistency Verification Summary Query
-- Confirm that no invalid rows penetrated the database engine.
-- =============================================================================
SELECT 'Subject Credits Invariant' AS invariant, COUNT(*) AS violations FROM subject WHERE credits NOT BETWEEN 1 AND 6
UNION ALL
SELECT 'Marks Invariant', COUNT(*) FROM marks WHERE marks_obtained < 0
UNION ALL
SELECT 'Assessment Weightage Invariant', COUNT(*) 
FROM (SELECT offering_id, SUM(weightage) AS total_wt FROM assessment GROUP BY offering_id HAVING total_wt > 100) AS bad_wt;
-- Expected Output: violations = 0 for all invariants.
