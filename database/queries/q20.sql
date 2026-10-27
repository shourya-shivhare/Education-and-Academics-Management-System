-- =============================================================================
-- q20.sql
-- Concept: CASE Expressions for Categorical Binning & Business Classification
-- Business Question: Categorize student enrollments in a given term into attendance
--                    compliance risk tiers and academic performance grade bands.
--
-- DBMS Concept Note:
-- The SQL standard CASE WHEN expression enables discrete categorical binning directly
-- inside the database query engine. Evaluating conditional ranges in SQL offloads
-- classification from backend Java code, keeping business logic in the DBMS.
-- =============================================================================

SELECT 
    s.roll_no,
    CONCAT(p.first_name, ' ', p.last_name) AS student_name,
    sub.code AS subject_code,
    att_summary.attendance_pct,
    CASE 
        WHEN att_summary.attendance_pct >= 85.0 THEN 'Good Standing (>=85%)'
        WHEN att_summary.attendance_pct >= 75.0 THEN 'Satisfactory (75-84%)'
        ELSE 'Detained / Critical Risk (<75%)'
    END AS attendance_category,
    e.final_marks,
    CASE 
        WHEN e.final_marks >= 90.0 THEN 'Distinction (O)'
        WHEN e.final_marks >= 70.0 THEN 'First Class (A/A+)'
        WHEN e.final_marks >= 50.0 THEN 'Second Class (B/B+)'
        WHEN e.final_marks >= 40.0 THEN 'Pass (C/P)'
        WHEN e.final_marks IS NOT NULL THEN 'Fail (F)'
        ELSE 'In Progress / Ungraded'
    END AS academic_grade_band
FROM enrollment e
JOIN student s ON e.student_id = s.student_id
JOIN person p ON s.student_id = p.person_id
JOIN course_offering co ON e.offering_id = co.offering_id
JOIN subject sub ON co.subject_id = sub.subject_id
LEFT JOIN (
    SELECT 
        enrollment_id,
        ROUND((SUM(CASE WHEN status IN ('Present','Late') THEN 1 ELSE 0 END) / COUNT(*)) * 100, 2) AS attendance_pct
    FROM attendance
    GROUP BY enrollment_id
) AS att_summary ON e.enrollment_id = att_summary.enrollment_id
WHERE co.term_id = ?
ORDER BY att_summary.attendance_pct ASC;
