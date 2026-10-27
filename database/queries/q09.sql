-- =============================================================================
-- q09.sql
-- Concept: Correlated Subquery in WHERE and SELECT clauses
-- Business Question: Which students scored above the overall historical average
--                    mark for their specific subject?
--
-- DBMS Concept Note:
-- A correlated subquery references values from outer query rows ('co.subject_id').
-- It is evaluated conceptually for each candidate outer row. The query shows both
-- a scalar correlated subquery in the SELECT projection and a filtering predicate.
-- =============================================================================

SELECT 
    s.roll_no,
    CONCAT(p.first_name, ' ', p.last_name) AS student_name,
    sub.code AS subject_code,
    sub.title AS subject_title,
    e.final_marks,
    (
        SELECT ROUND(AVG(inner_e.final_marks), 2)
        FROM enrollment inner_e
        JOIN course_offering inner_co ON inner_e.offering_id = inner_co.offering_id
        WHERE inner_co.subject_id = co.subject_id
          AND inner_e.final_marks IS NOT NULL
    ) AS subject_avg_marks
FROM enrollment e
JOIN student s ON e.student_id = s.student_id
JOIN person p ON s.student_id = p.person_id
JOIN course_offering co ON e.offering_id = co.offering_id
JOIN subject sub ON co.subject_id = sub.subject_id
WHERE e.final_marks IS NOT NULL
  AND e.final_marks > (
      SELECT AVG(inner_e.final_marks)
      FROM enrollment inner_e
      JOIN course_offering inner_co ON inner_e.offering_id = inner_co.offering_id
      WHERE inner_co.subject_id = co.subject_id
        AND inner_e.final_marks IS NOT NULL
  )
ORDER BY sub.code, e.final_marks DESC;
