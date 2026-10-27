-- =============================================================================
-- q11.sql
-- Concept: Comprehensive SQL Aggregate Functions (COUNT, SUM, AVG, MIN, MAX, STDDEV)
-- Business Question: What is the comprehensive statistical mark profile (count, average,
--                    lowest, highest, standard deviation, and pass total) for each subject?
--
-- DBMS Concept Note:
-- Demonstrates SQL standard aggregate functions over partitioned groups. Uses
-- conditional aggregation `SUM(CASE WHEN ... THEN 1 ELSE 0 END)` to count subsets
-- within a single query pass without requiring extra joins or subqueries.
-- =============================================================================

SELECT 
    sub.code AS subject_code,
    sub.title AS subject_title,
    sub.credits,
    COUNT(e.enrollment_id) AS total_graded_students,
    ROUND(AVG(e.final_marks), 2) AS average_score,
    MIN(e.final_marks) AS minimum_score,
    MAX(e.final_marks) AS maximum_score,
    ROUND(STDDEV_SAMP(e.final_marks), 2) AS score_std_dev,
    SUM(CASE WHEN e.final_marks >= 40.0 THEN 1 ELSE 0 END) AS passing_students_count
FROM subject sub
JOIN course_offering co ON sub.subject_id = co.subject_id
JOIN enrollment e ON co.offering_id = e.offering_id
WHERE e.final_marks IS NOT NULL
GROUP BY sub.subject_id, sub.code, sub.title, sub.credits
ORDER BY average_score DESC;
