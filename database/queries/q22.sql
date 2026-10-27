-- =============================================================================
-- q22.sql
-- Concept: Hierarchical Aggregation with GROUP BY ... WITH ROLLUP
-- Business Question: What are the enrollment totals and average marks across departments
--                    and programs, including department subtotals and institutional grand totals?
--
-- DBMS Concept Note:
-- The WITH ROLLUP modifier generates super-aggregate summary rows along an n-dimensional
-- hierarchy (Program -> Department -> Institutional Grand Total). COALESCE() is used to
-- replace NULL grouping indicators with readable hierarchical subtotal labels.
-- =============================================================================

SELECT 
    COALESCE(d.name, '--- ALL DEPARTMENTS (GRAND TOTAL) ---') AS department_name,
    COALESCE(p.name, '--- DEPARTMENT SUBTOTAL ---') AS program_name,
    COUNT(e.enrollment_id) AS total_enrollments,
    ROUND(AVG(e.final_marks), 2) AS avg_final_marks,
    SUM(CASE WHEN e.final_marks >= 40.0 THEN 1 ELSE 0 END) AS passed_enrollments
FROM department d
JOIN program p ON d.department_id = p.department_id
JOIN student s ON p.program_id = s.program_id
JOIN enrollment e ON s.student_id = e.student_id
GROUP BY d.name, p.name WITH ROLLUP;
