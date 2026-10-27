-- =============================================================================
-- q21.sql
-- Concept: Date and Temporal Functions (Monthly Attendance Aggregation)
-- Business Question: What is the month-by-month attendance progression (total entries,
--                    presents, absents, and compliance percentage) for a given academic term?
--
-- DBMS Concept Note:
-- Demonstrates SQL date truncation, formatting, and temporal aggregation functions
-- (DATE_FORMAT, MONTHNAME, MONTH). Grouping by temporal intervals allows time-series
-- reporting on student institutional engagement.
-- =============================================================================

SELECT 
    DATE_FORMAT(a.class_date, '%Y-%m') AS year_month,
    MONTHNAME(a.class_date) AS month_name,
    COUNT(a.attendance_id) AS total_attendance_entries,
    SUM(CASE WHEN a.status = 'Present' THEN 1 ELSE 0 END) AS present_count,
    SUM(CASE WHEN a.status = 'Late' THEN 1 ELSE 0 END) AS late_count,
    SUM(CASE WHEN a.status = 'Absent' THEN 1 ELSE 0 END) AS absent_count,
    ROUND(
        (SUM(CASE WHEN a.status IN ('Present', 'Late') THEN 1 ELSE 0 END) / COUNT(a.attendance_id)) * 100, 
        2
    ) AS monthly_attendance_rate
FROM attendance a
JOIN enrollment e ON a.enrollment_id = e.enrollment_id
JOIN course_offering co ON e.offering_id = co.offering_id
WHERE co.term_id = ?
GROUP BY DATE_FORMAT(a.class_date, '%Y-%m'), MONTHNAME(a.class_date), MONTH(a.class_date)
ORDER BY year_month ASC;
