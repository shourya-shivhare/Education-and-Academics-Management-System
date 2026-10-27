-- =============================================================================
-- q13.sql
-- Concept: HAVING Clause Filtering Aggregate Results
-- Business Question: Which subjects have an overall student pass rate below 60%
--                    (identifying high-difficulty academic courses)?
--
-- DBMS Concept Note:
-- The WHERE clause filters base tuples prior to aggregation; the HAVING clause
-- filters grouped tuples AFTER aggregation. The HAVING condition evaluates the
-- computed ratio of passing students to total students evaluated.
-- =============================================================================

SELECT 
    sub.code AS subject_code,
    sub.title AS subject_title,
    d.name AS department_name,
    COUNT(e.enrollment_id) AS total_students_evaluated,
    SUM(CASE WHEN e.final_marks >= 40.0 THEN 1 ELSE 0 END) AS passed_students,
    ROUND((SUM(CASE WHEN e.final_marks >= 40.0 THEN 1 ELSE 0 END) / COUNT(e.enrollment_id)) * 100, 2) AS pass_percentage
FROM subject sub
JOIN department d ON sub.department_id = d.department_id
JOIN course_offering co ON sub.subject_id = co.subject_id
JOIN enrollment e ON co.offering_id = e.offering_id
WHERE e.final_marks IS NOT NULL
GROUP BY sub.subject_id, sub.code, sub.title, d.name
HAVING pass_percentage < 60.00
   AND total_students_evaluated >= 5
ORDER BY pass_percentage ASC;
