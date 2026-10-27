-- =============================================================================
-- q08.sql
-- Concept: Quantified Comparison Subqueries (ANY and ALL)
-- Business Question: Which students scored higher in a subject than ALL students
--                    enrolled in a specified benchmark course offering?
--
-- DBMS Concept Note:
-- Demonstrates the '> ALL (subquery)' operator, which evaluates to TRUE only if
-- the left operand is strictly greater than every non-null value returned by the
-- subquery (equivalent to > MAX(subquery) over a non-empty set).
-- =============================================================================

SELECT 
    s.student_id,
    s.roll_no,
    CONCAT(p.first_name, ' ', p.last_name) AS student_name,
    sub.code AS subject_code,
    e.final_marks,
    e.grade
FROM enrollment e
JOIN student s ON e.student_id = s.student_id
JOIN person p ON s.student_id = p.person_id
JOIN course_offering co ON e.offering_id = co.offering_id
JOIN subject sub ON co.subject_id = sub.subject_id
WHERE e.final_marks IS NOT NULL
  AND e.final_marks > ALL (
      -- All marks achieved in the benchmark offering
      SELECT benchmark_e.final_marks
      FROM enrollment benchmark_e
      WHERE benchmark_e.offering_id = ?
        AND benchmark_e.final_marks IS NOT NULL
  )
ORDER BY e.final_marks DESC;
