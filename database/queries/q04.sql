-- =============================================================================
-- q04.sql
-- Concept: FULL OUTER JOIN simulation (LEFT JOIN UNION RIGHT JOIN)
-- Business Question: Compare all departments and academic programs: which departments
--                    have no programs, and which programs belong to an unassigned department?
--
-- DBMS Concept Note:
-- MySQL 8 does not provide a native `FULL OUTER JOIN` keyword. In relational database
-- design, a full outer join is mathematically equivalent to the set union of a LEFT
-- OUTER JOIN and a RIGHT OUTER JOIN, with duplicate matched tuples eliminated by the
-- UNION operator.
-- =============================================================================

-- Left Outer Join: preserves all departments
SELECT 
    d.department_id,
    d.name AS department_name,
    p.program_id,
    p.name AS program_name,
    p.degree
FROM department d
LEFT JOIN program p ON d.department_id = p.department_id

UNION

-- Right Outer Join: preserves all programs
SELECT 
    d.department_id,
    d.name AS department_name,
    p.program_id,
    p.name AS program_name,
    p.degree
FROM department d
RIGHT JOIN program p ON d.department_id = p.department_id;
