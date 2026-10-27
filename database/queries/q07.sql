-- =============================================================================
-- q07.sql
-- Concept: Correlated Subqueries with EXISTS and NOT EXISTS
-- Business Question: Which students have active course enrollments in a given term
--                    but have NOT paid their tuition fee for that term?
--
-- DBMS Concept Note:
-- Demonstrates relational semi-join (EXISTS) and anti-join (NOT EXISTS). The subqueries
-- correlate to outer row 's.student_id'. The MySQL engine stops evaluating the subquery
-- as soon as the first matching tuple is found (early termination short-circuit).
-- =============================================================================

SELECT 
    s.student_id,
    s.roll_no,
    CONCAT(p.first_name, ' ', p.last_name) AS student_name,
    p.email,
    prog.name AS program_name
FROM student s
JOIN person p ON s.student_id = p.person_id
JOIN program prog ON s.program_id = prog.program_id
WHERE EXISTS (
    -- Positive semi-join: Student has at least one enrollment in this term
    SELECT 1 
    FROM enrollment e
    JOIN course_offering co ON e.offering_id = co.offering_id
    WHERE e.student_id = s.student_id 
      AND co.term_id = ?
)
AND NOT EXISTS (
    -- Anti-join: Student does not have a confirmed 'Paid' status in fee_payment for this term
    SELECT 1 
    FROM fee_payment fp
    WHERE fp.student_id = s.student_id 
      AND fp.term_id = ?
      AND fp.status = 'Paid'
)
ORDER BY s.roll_no;
