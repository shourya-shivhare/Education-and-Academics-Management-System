-- =============================================================================
-- q12.sql
-- Concept: Multi-column GROUP BY with Conditional Aggregates
-- Business Question: How many active, graduated, and inactive students are registered
--                    in each academic program within every department?
--
-- DBMS Concept Note:
-- Demonstrates multi-level grouping semantics. Tuples are partitioned into groups
-- defined by distinct combinations of (department, program). Non-aggregate columns
-- in the SELECT list are strictly functional dependencies of the grouping keys.
-- =============================================================================

SELECT 
    d.name AS department_name,
    p.name AS program_name,
    p.degree,
    COUNT(s.student_id) AS total_students,
    SUM(CASE WHEN s.status = 'Active' THEN 1 ELSE 0 END) AS active_students,
    SUM(CASE WHEN s.status = 'Graduated' THEN 1 ELSE 0 END) AS graduated_students,
    SUM(CASE WHEN s.status IN ('Dropped', 'Suspended') THEN 1 ELSE 0 END) AS inactive_students
FROM department d
JOIN program p ON d.department_id = p.department_id
LEFT JOIN student s ON p.program_id = s.program_id
GROUP BY d.department_id, d.name, p.program_id, p.name, p.degree
ORDER BY d.name, p.name;
