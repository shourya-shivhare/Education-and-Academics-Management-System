-- =============================================================================
-- q14.sql
-- Concept: HAVING with Multi-Table JOIN (Teaching Load Analysis)
-- Business Question: Which faculty members are teaching more than 2 course offerings
--                    in a given academic term?
--
-- DBMS Concept Note:
-- Combines equijoins across faculty, person, department, course_offering, and term
-- with aggregate grouping and a post-aggregation HAVING threshold filter. Illustrates
-- workload enforcement checks at the relational query layer.
-- =============================================================================

SELECT 
    f.faculty_id,
    f.employee_no,
    CONCAT(p.first_name, ' ', p.last_name) AS faculty_name,
    d.name AS department_name,
    t.name AS term_name,
    COUNT(co.offering_id) AS total_offerings_taught,
    SUM(co.capacity) AS total_student_capacity
FROM faculty f
JOIN person p ON f.faculty_id = p.person_id
JOIN department d ON f.department_id = d.department_id
JOIN course_offering co ON f.faculty_id = co.faculty_id
JOIN term t ON co.term_id = t.term_id
WHERE co.term_id = ?
GROUP BY f.faculty_id, f.employee_no, p.first_name, p.last_name, d.name, t.name
HAVING total_offerings_taught >= 2
ORDER BY total_offerings_taught DESC, faculty_name ASC;
