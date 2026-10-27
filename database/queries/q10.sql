-- =============================================================================
-- q10.sql
-- Concept: Derived Table (Subquery in FROM clause)
-- Business Question: What is the ranking of academic departments by the average
--                    of their students' individual grade averages?
--
-- DBMS Concept Note:
-- Demonstrates two-stage aggregation using an inline derived relation (table subquery
-- in FROM). The inner query calculates student GPAs, while the outer query computes
-- the mean of those GPAs grouped by department.
-- =============================================================================

SELECT 
    student_avgs.department_name,
    COUNT(student_avgs.student_id) AS evaluated_students_count,
    ROUND(AVG(student_avgs.student_gpa), 2) AS dept_academic_performance
FROM (
    -- Subquery in FROM: compute GPA / average marks per student
    SELECT 
        d.department_id,
        d.name AS department_name,
        s.student_id,
        AVG(e.final_marks) AS student_gpa
    FROM student s
    JOIN program prog ON s.program_id = prog.program_id
    JOIN department d ON prog.department_id = d.department_id
    JOIN enrollment e ON s.student_id = e.student_id
    WHERE e.final_marks IS NOT NULL
    GROUP BY d.department_id, d.name, s.student_id
) AS student_avgs
GROUP BY student_avgs.department_id, student_avgs.department_name
ORDER BY dept_academic_performance DESC;
