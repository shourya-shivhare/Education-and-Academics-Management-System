-- =============================================================================
-- R__functions.sql
-- Repeatable Flyway Migration: User-Defined Functions (UDFs)
-- Database: edu_db | Flyway User: flyway_user
--
-- DBMS Concepts Demonstrated:
--   1. Deterministic vs. Non-Deterministic Routines:
--      - DETERMINISTIC: Always returns identical output for identical inputs.
--      - READS SQL DATA: Informs query optimizer that the routine reads data from
--        tables but performs no DML modifications (side-effect free).
--   2. Encapsulation of Derived Attributes:
--      - Academic rules (grade points, attendance percentages, CGPA) are computed
--        directly inside the DBMS kernel instead of duplicated across application tiers.
--   3. Re-runnability:
--      - Uses DROP FUNCTION IF EXISTS before each CREATE FUNCTION.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. fn_grade_point(p_marks)
-- Returns the 10-point grade scale point (DECIMAL(3,1)) corresponding to raw marks.
-- Looks up the reference grade_scale table populated in V5.
-- -----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS fn_grade_point;

DELIMITER $$
CREATE FUNCTION fn_grade_point(p_marks DECIMAL(6,2))
RETURNS DECIMAL(3,1)
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE v_point DECIMAL(3,1);

    -- Return NULL if marks have not yet been evaluated
    IF p_marks IS NULL THEN
        RETURN NULL;
    END IF;

    -- Lookup grade point from the grade_scale reference table
    SELECT grade_point INTO v_point
    FROM grade_scale
    WHERE p_marks >= min_percent 
      AND p_marks <= max_percent
    LIMIT 1;

    -- Default fallback if out of bounds (e.g. negative or >100)
    RETURN COALESCE(v_point, 0.0);
END$$
DELIMITER ;


-- -----------------------------------------------------------------------------
-- 2. fn_attendance_pct(p_enrollment_id)
-- Computes the percentage of classes attended for a given course enrollment:
-- (classes_attended / classes_held) * 100.
-- Statuses 'Present' and 'Late' count toward attendance credit.
-- -----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS fn_attendance_pct;

DELIMITER $$
CREATE FUNCTION fn_attendance_pct(p_enrollment_id INT)
RETURNS DECIMAL(5,2)
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE v_held INT DEFAULT 0;
    DECLARE v_attended INT DEFAULT 0;

    -- Aggregate attendance records for this enrollment
    SELECT 
        COUNT(*),
        COALESCE(SUM(CASE WHEN status IN ('Present', 'Late') THEN 1 ELSE 0 END), 0)
    INTO v_held, v_attended
    FROM attendance
    WHERE enrollment_id = p_enrollment_id;

    -- If no class sessions have taken place yet, default to 100.00% (no shortage)
    IF v_held = 0 THEN
        RETURN 100.00;
    END IF;

    RETURN ROUND((v_attended / v_held) * 100.0, 2);
END$$
DELIMITER ;


-- -----------------------------------------------------------------------------
-- 3. fn_cgpa(p_student_id)
-- Calculates Cumulative Grade Point Average (CGPA) for a student across all
-- completed courses:
-- CGPA = SUM(credits * grade_point) / SUM(credits).
-- -----------------------------------------------------------------------------
DROP FUNCTION IF EXISTS fn_cgpa;

DELIMITER $$
CREATE FUNCTION fn_cgpa(p_student_id INT)
RETURNS DECIMAL(4,2)
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE v_quality_points DECIMAL(10,2) DEFAULT 0.0;
    DECLARE v_total_credits INT DEFAULT 0;

    -- Sum weighted quality points and credits for all completed courses
    SELECT 
        COALESCE(SUM(sub.credits * gs.grade_point), 0.0),
        COALESCE(SUM(sub.credits), 0)
    INTO v_quality_points, v_total_credits
    FROM enrollment e
    JOIN course_offering co ON e.offering_id = co.offering_id
    JOIN subject sub ON co.subject_id = sub.subject_id
    JOIN grade_scale gs ON e.grade = gs.grade_letter
    WHERE e.student_id = p_student_id
      AND e.final_marks IS NOT NULL;

    -- If no courses have been graded, return 0.00
    IF v_total_credits = 0 THEN
        RETURN 0.00;
    END IF;

    RETURN ROUND(v_quality_points / v_total_credits, 2);
END$$
DELIMITER ;
