-- =============================================================================
-- q16.sql
-- Concept: Offset Window Function (LAG for Running Term Performance Trajectory)
-- Business Question: For a specific student, what was their GPA term-by-term, their
--                    previous term's GPA, and the performance delta (improvement/drop)?
--
-- DBMS Concept Note:
-- The LAG(expr, offset) window function accesses data from a preceding row at a
-- physical offset within the window partition without executing a self-join. Useful
-- for calculating term-over-term growth and identifying student academic drift.
-- =============================================================================

WITH student_term_perf AS (
    SELECT 
        s.student_id,
        s.roll_no,
        t.term_id,
        t.name AS term_name,
        t.start_date,
        ROUND(AVG(e.final_marks), 2) AS term_gpa
    FROM student s
    JOIN enrollment e ON s.student_id = e.student_id
    JOIN course_offering co ON e.offering_id = co.offering_id
    JOIN term t ON co.term_id = t.term_id
    WHERE s.student_id = ?
      AND e.final_marks IS NOT NULL
    GROUP BY s.student_id, s.roll_no, t.term_id, t.name, t.start_date
)
SELECT 
    roll_no,
    term_name,
    term_gpa,
    LAG(term_gpa, 1) OVER (ORDER BY start_date) AS previous_term_gpa,
    ROUND(term_gpa - LAG(term_gpa, 1) OVER (ORDER BY start_date), 2) AS gpa_change
FROM student_term_perf
ORDER BY start_date ASC;
