-- =============================================================================
-- q06.sql
-- Concept: Subquery with IN predicate
-- Business Question: Which students have taken at least one course taught by a given
--                    faculty member?
--
-- DBMS Concept Note:
-- The IN subquery produces an intermediate set of student_ids. The MySQL optimizer
-- typically transforms this into a semi-join or executes it via materialized
-- subquery lookup depending on cardinality and available indexes.
-- =============================================================================

SELECT 
    s.student_id,
    s.roll_no,
    CONCAT(p.first_name, ' ', p.last_name) AS student_name,
    prog.name AS program_name,
    s.status
FROM student s
JOIN person p ON s.student_id = p.person_id
JOIN program prog ON s.program_id = prog.program_id
WHERE s.student_id IN (
    SELECT DISTINCT e.student_id
    FROM enrollment e
    JOIN course_offering co ON e.offering_id = co.offering_id
    WHERE co.faculty_id = ?
)
ORDER BY s.roll_no;
