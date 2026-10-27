-- =============================================================================
-- R__views.sql
-- Repeatable Flyway Migration: Relational Views & Security Projections
-- Database: edu_db | Flyway User: flyway_user
--
-- DBMS Concepts Demonstrated:
--   1. Relational Views as Logical Data Independence:
--      - Presents pre-joined, formatted data abstractions without storing derived
--        attributes (CGPA, attendance percentage, shortage flag) in physical tables.
--   2. Security Projections (Column-level least privilege):
--      - 'v_student_public' hides Personally Identifiable Information (PII) such as
--        date of birth, residential address, pin code, and financial payment records.
--   3. Updatable Views and WITH CHECK OPTION:
--      - 'v_updatable_demo' satisfies Codd's rule for view updates (1:1 bijection
--        with base table) and enforces constraint filtering on writes.
--   4. Theory of Non-Updatability on Aggregate Views (Detailed explanation below).
-- =============================================================================

-- =============================================================================
-- CONCEPTUAL DISCUSSION: Why Aggregate Views Are NOT Updatable in a DBMS
-- =============================================================================
-- In relational database theory (Codd's Rule 6: View Updating Rule), a view is
-- updatable if and only if there exists an unambiguous, bidirectional mapping
-- (bijection) between tuples in the view and tuples in the underlying base table.
--
-- When an UPDATE, INSERT, or DELETE is executed on a view, the DBMS query engine
-- must translate that operation into an exact, deterministic modification on the
-- base relation. Aggregate views (containing SUM, AVG, COUNT, MIN, MAX, or GROUP BY)
-- fundamentally violate this invertibility property:
--
-- 1. Loss of Row Identity (Many-to-One Reduction):
--    An aggregate function condenses multiple base table rows into a single summary
--    value. For example, if 'v_dept_performance' shows an avg_final_marks of 72.50
--    for a department computed from 300 student enrollments, an UPDATE statement:
--      UPDATE v_dept_performance SET avg_final_marks = 75.00 WHERE department_id = 1;
--    is mathematically and logically ill-defined. The DBMS cannot determine which of
--    the 300 underlying students should receive mark increases, nor how the 2.50 delta
--    should be partitioned among them.
--
-- 2. Non-Invertible Synthetic Tuples:
--    Aggregates synthesize values that do not exist as physical fields in any base row.
--    Because a sum or average cannot identify which specific primary keys generated
--    the aggregate, the engine cannot construct a valid base-table WHERE clause.
--
-- 3. MySQL 8 View Updatability Specification:
--    Per MySQL 8 reference manual, a view is NOT updatable if its SELECT contains:
--      - Aggregate functions (SUM, MIN, MAX, COUNT, AVG, STDDEV)
--      - The DISTINCT keyword
--      - A GROUP BY or HAVING clause
--      - A UNION or UNION ALL operator
--      - Subqueries in the SELECT list
--      - Joins where multiple base tables would require simultaneous modification
--
-- Consequently, views 1 through 4 below are read-only analytical abstractions,
-- while view 6 is constructed deliberately as a single-table 1:1 view to demonstrate
-- full updatability with constraint checking.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. v_student_transcript
-- Joined comprehensive academic transcript displaying courses, terms, credits,
-- instructor names, marks, letter grades, and computed quality points.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_student_transcript AS
SELECT 
    s.student_id,
    s.roll_no,
    CONCAT(p_stu.first_name, ' ', p_stu.last_name) AS student_name,
    prog.name AS program_name,
    t.term_id,
    t.name AS term_name,
    t.start_date AS term_start_date,
    sub.subject_id,
    sub.code AS subject_code,
    sub.title AS subject_title,
    sub.credits,
    CONCAT(p_fac.first_name, ' ', p_fac.last_name) AS instructor_name,
    e.final_marks,
    e.grade,
    gs.grade_point,
    (sub.credits * gs.grade_point) AS quality_points
FROM student s
JOIN person p_stu ON s.student_id = p_stu.person_id
JOIN program prog ON s.program_id = prog.program_id
JOIN enrollment e ON s.student_id = e.student_id
JOIN course_offering co ON e.offering_id = co.offering_id
JOIN term t ON co.term_id = t.term_id
JOIN subject sub ON co.subject_id = sub.subject_id
JOIN faculty f ON co.faculty_id = f.faculty_id
JOIN person p_fac ON f.faculty_id = p_fac.person_id
LEFT JOIN grade_scale gs ON e.grade = gs.grade_letter;


-- -----------------------------------------------------------------------------
-- 2. v_attendance_summary
-- Summarizes classes held, classes attended, attendance percentage, and flags
-- attendance shortage (< 75% detention threshold) per course enrollment.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_attendance_summary AS
SELECT 
    e.enrollment_id,
    s.student_id,
    s.roll_no,
    CONCAT(p.first_name, ' ', p.last_name) AS student_name,
    co.offering_id,
    sub.code AS subject_code,
    sub.title AS subject_title,
    t.term_id,
    t.name AS term_name,
    COUNT(a.attendance_id) AS classes_held,
    COALESCE(SUM(CASE WHEN a.status IN ('Present', 'Late') THEN 1 ELSE 0 END), 0) AS classes_attended,
    COALESCE(SUM(CASE WHEN a.status = 'Absent' THEN 1 ELSE 0 END), 0) AS classes_absent,
    ROUND(
        CASE 
            WHEN COUNT(a.attendance_id) = 0 THEN 100.00
            ELSE (COALESCE(SUM(CASE WHEN a.status IN ('Present', 'Late') THEN 1 ELSE 0 END), 0) / COUNT(a.attendance_id)) * 100.0
        END, 
        2
    ) AS attendance_percent,
    CASE 
        WHEN COUNT(a.attendance_id) > 0 
         AND (COALESCE(SUM(CASE WHEN a.status IN ('Present', 'Late') THEN 1 ELSE 0 END), 0) / COUNT(a.attendance_id)) < 0.75 
        THEN TRUE 
        ELSE FALSE 
    END AS is_shortage
FROM enrollment e
JOIN student s ON e.student_id = s.student_id
JOIN person p ON s.student_id = p.person_id
JOIN course_offering co ON e.offering_id = co.offering_id
JOIN term t ON co.term_id = t.term_id
JOIN subject sub ON co.subject_id = sub.subject_id
LEFT JOIN attendance a ON e.enrollment_id = a.enrollment_id
GROUP BY 
    e.enrollment_id, s.student_id, s.roll_no, p.first_name, p.last_name,
    co.offering_id, sub.code, sub.title, t.term_id, t.name;


-- -----------------------------------------------------------------------------
-- 3. v_faculty_workload
-- Teaching workload metrics per faculty member per term: count of course offerings,
-- student enrollment capacity, credit hours, and max_load threshold check.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_faculty_workload AS
SELECT 
    f.faculty_id,
    f.employee_no,
    CONCAT(p.first_name, ' ', p.last_name) AS faculty_name,
    d.department_id,
    d.name AS department_name,
    t.term_id,
    t.name AS term_name,
    COUNT(DISTINCT co.offering_id) AS offerings_count,
    COUNT(DISTINCT e.enrollment_id) AS total_enrolled_students,
    COALESCE(SUM(sub.credits), 0) AS total_credit_hours,
    f.max_load,
    CASE 
        WHEN COUNT(DISTINCT co.offering_id) > COALESCE(f.max_load, 4) THEN TRUE 
        ELSE FALSE 
    END AS is_overloaded
FROM faculty f
JOIN person p ON f.faculty_id = p.person_id
JOIN department d ON f.department_id = d.department_id
JOIN course_offering co ON f.faculty_id = co.faculty_id
JOIN term t ON co.term_id = t.term_id
JOIN subject sub ON co.subject_id = sub.subject_id
LEFT JOIN enrollment e ON co.offering_id = e.offering_id
GROUP BY 
    f.faculty_id, f.employee_no, p.first_name, p.last_name, 
    d.department_id, d.name, t.term_id, t.name, f.max_load;


-- -----------------------------------------------------------------------------
-- 4. v_dept_performance
-- Academic performance metrics aggregated at the departmental level: student count,
-- total course enrollments, average marks, and overall pass percentage.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_dept_performance AS
SELECT 
    d.department_id,
    d.name AS department_name,
    COUNT(DISTINCT p.program_id) AS total_programs,
    COUNT(DISTINCT f.faculty_id) AS total_faculty,
    COUNT(DISTINCT s.student_id) AS total_students_enrolled,
    COUNT(DISTINCT e.enrollment_id) AS total_course_enrollments,
    ROUND(AVG(e.final_marks), 2) AS avg_final_marks,
    ROUND(
        (SUM(CASE WHEN e.final_marks >= 40.0 THEN 1 ELSE 0 END) / 
         NULLIF(COUNT(e.final_marks), 0)) * 100.0, 
        2
    ) AS pass_percentage
FROM department d
LEFT JOIN program p ON d.department_id = p.department_id
LEFT JOIN faculty f ON d.department_id = f.department_id
LEFT JOIN student s ON p.program_id = s.program_id
LEFT JOIN enrollment e ON s.student_id = e.student_id
GROUP BY d.department_id, d.name;


-- -----------------------------------------------------------------------------
-- 5. v_student_public (Security View / PII Masking)
-- Projected view for least-privilege access (student_role / public directory).
-- Omits sensitive personal data: date_of_birth, address_line, city, state,
-- pin_code, phone numbers, and financial fee payments.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_student_public AS
SELECT 
    s.student_id,
    s.roll_no,
    p.first_name,
    p.last_name,
    p.gender,
    prog.program_id,
    prog.name AS program_name,
    prog.degree,
    d.name AS department_name,
    s.admission_date,
    s.status,
    s.current_semester
FROM student s
JOIN person p ON s.student_id = p.person_id
JOIN program prog ON s.program_id = prog.program_id
JOIN department d ON prog.department_id = d.department_id;


-- -----------------------------------------------------------------------------
-- 6. v_updatable_demo (Updatable View with WITH CHECK OPTION)
-- Single-table view on classroom with capacity >= 20.
-- Demonstrates Codd's updatable view criteria: 1:1 mapping with base relation.
-- WITH CHECK OPTION prevents INSERTs or UPDATEs that violate 'capacity >= 20'.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_updatable_demo AS
SELECT 
    room_id,
    building,
    room_no,
    capacity
FROM classroom
WHERE capacity >= 20
WITH CHECK OPTION;
