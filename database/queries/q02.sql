-- =============================================================================
-- q02.sql
-- Concept: LEFT OUTER JOIN
-- Business Question: What are all subjects in the catalog and their offering counts,
--                    including subjects that have never been offered (count = 0)?
--
-- DBMS Concept Note:
-- An outer join preserves all rows from the left relation ('subject') even when
-- no matching tuples exist in the right relation ('course_offering'). Unmatched
-- right-side columns evaluate to NULL, allowing COUNT(co.offering_id) to return 0.
-- =============================================================================

SELECT 
    d.name AS department_name,
    sub.code AS subject_code,
    sub.title AS subject_title,
    sub.credits,
    COUNT(co.offering_id) AS total_offerings_count
FROM subject sub
JOIN department d ON sub.department_id = d.department_id
LEFT JOIN course_offering co ON sub.subject_id = co.subject_id
GROUP BY d.name, sub.subject_id, sub.code, sub.title, sub.credits
ORDER BY total_offerings_count ASC, sub.code ASC;
