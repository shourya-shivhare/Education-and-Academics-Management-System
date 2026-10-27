-- =============================================================================
-- q15.sql
-- Concept: Analytic Window Functions (RANK and DENSE_RANK)
-- Business Question: Within each academic department, how do students rank based
--                    on their average marks, demonstrating tie-handling differences?
--
-- DBMS Concept Note:
-- Window functions compute rankings across an execution partition without collapsing
-- individual rows into a single summary tuple. RANK() produces gaps in ranking after
-- ties (e.g. 1, 2, 2, 4), whereas DENSE_RANK() assigns contiguous integers (1, 2, 2, 3).
-- =============================================================================

SELECT 
    d.name AS department_name,
    s.roll_no,
    CONCAT(p.first_name, ' ', p.last_name) AS student_name,
    ROUND(AVG(e.final_marks), 2) AS student_gpa,
    RANK() OVER (
        PARTITION BY d.department_id 
        ORDER BY AVG(e.final_marks) DESC
    ) AS rank_in_dept,
    DENSE_RANK() OVER (
        PARTITION BY d.department_id 
        ORDER BY AVG(e.final_marks) DESC
    ) AS dense_rank_in_dept
FROM student s
JOIN person p ON s.student_id = p.person_id
JOIN program prog ON s.program_id = prog.program_id
JOIN department d ON prog.department_id = d.department_id
JOIN enrollment e ON s.student_id = e.student_id
WHERE e.final_marks IS NOT NULL
GROUP BY d.department_id, d.name, s.student_id, s.roll_no, p.first_name, p.last_name
ORDER BY d.name, rank_in_dept;
