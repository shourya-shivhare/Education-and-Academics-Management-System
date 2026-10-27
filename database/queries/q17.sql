-- =============================================================================
-- q17.sql
-- Concept: Common Table Expression (CTE) - Two-Stage CGPA Computation
-- Business Question: What is the cumulative grade point average (CGPA) of all students
--                    in a given academic program across completed terms?
--
-- DBMS Concept Note:
-- Uses modular SQL CTEs ('WITH clause') to break complex multi-step computations into
-- readable, non-materialized views. Stage 1 computes weighted quality points per course
-- (credits * grade_point); Stage 2 aggregates cumulative totals per student.
-- =============================================================================

WITH term_course_points AS (
    -- Stage 1: Compute credit points earned per completed course
    SELECT 
        e.student_id,
        co.term_id,
        sub.credits,
        gs.grade_point,
        (sub.credits * gs.grade_point) AS quality_points
    FROM enrollment e
    JOIN course_offering co ON e.offering_id = co.offering_id
    JOIN subject sub ON co.subject_id = sub.subject_id
    JOIN grade_scale gs ON e.grade = gs.grade_letter
    WHERE e.final_marks IS NOT NULL
),
student_cumulative AS (
    -- Stage 2: Aggregate cumulative points and credits per student
    SELECT 
        tcp.student_id,
        SUM(tcp.quality_points) AS total_quality_points,
        SUM(tcp.credits) AS total_credits_earned,
        ROUND(SUM(tcp.quality_points) / SUM(tcp.credits), 2) AS cgpa
    FROM term_course_points tcp
    GROUP BY tcp.student_id
)
SELECT 
    s.student_id,
    s.roll_no,
    CONCAT(p.first_name, ' ', p.last_name) AS student_name,
    prog.name AS program_name,
    sc.total_credits_earned,
    sc.cgpa
FROM student_cumulative sc
JOIN student s ON sc.student_id = s.student_id
JOIN person p ON s.student_id = p.person_id
JOIN program prog ON s.program_id = prog.program_id
WHERE s.program_id = ?
ORDER BY sc.cgpa DESC;
