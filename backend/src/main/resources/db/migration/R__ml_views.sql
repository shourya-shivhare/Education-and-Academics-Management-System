-- =============================================================================
-- R__ml_views.sql
-- Repeatable ML feature projections for the internal ml_readonly user.
--
-- v_ml_features contains only information available at the middle of a term.
-- v_ml_labels is deliberately separate: final grade/detention is post-term
-- outcome data and must never be supplied to a training feature query.
--
-- Assignment submission rate is a proxy because this schema has no assignment
-- submission table: an assignment is counted as submitted when a marks row
-- exists for that assessment. Fee delay is also a proxy because fee_payment has
-- no due-date column: pending payments are measured from term start to the
-- mid-term snapshot date.
-- =============================================================================

CREATE OR REPLACE VIEW v_ml_features AS
WITH
term_window AS (
    SELECT
        term_id,
        start_date,
        end_date,
        DATE_ADD(start_date, INTERVAL FLOOR(DATEDIFF(end_date, start_date) / 2) DAY) AS midterm_date
    FROM term
),
student_terms AS (
    SELECT DISTINCT
        e.student_id,
        tw.term_id,
        s.program_id,
        GREATEST(1, CEIL(TIMESTAMPDIFF(MONTH, s.admission_date, tw.start_date) / 12)) AS year_of_study,
        tw.start_date,
        tw.midterm_date
    FROM enrollment e
    JOIN course_offering co ON co.offering_id = e.offering_id
    JOIN term_window tw ON tw.term_id = co.term_id
    JOIN student s ON s.student_id = e.student_id
),
attendance_midterm AS (
    SELECT
        e.student_id,
        co.term_id,
        ROUND(
            100.0 * SUM(CASE WHEN a.status IN ('Present', 'Late') THEN 1 ELSE 0 END)
            / NULLIF(COUNT(a.attendance_id), 0), 2
        ) AS attendance_pct_midterm
    FROM enrollment e
    JOIN course_offering co ON co.offering_id = e.offering_id
    JOIN term_window tw ON tw.term_id = co.term_id
    LEFT JOIN attendance a
        ON a.enrollment_id = e.enrollment_id
       AND a.class_date <= tw.midterm_date
    GROUP BY e.student_id, co.term_id
),
assessment_midterm AS (
    SELECT
        e.student_id,
        co.term_id,
        ROUND(AVG(
            CASE WHEN a.assessment_no = 1
                 THEN m.marks_obtained / NULLIF(a.max_marks, 0) * 100 END
        ), 2) AS internal1_avg_pct,
        ROUND(AVG(
            CASE WHEN a.assessment_no = 2
                 THEN m.marks_obtained / NULLIF(a.max_marks, 0) * 100 END
        ), 2) AS internal2_avg_pct,
        ROUND(100.0 * SUM(
            CASE WHEN LOWER(a.title) LIKE '%assignment%' AND m.enrollment_id IS NOT NULL THEN 1 ELSE 0 END
        ) / NULLIF(SUM(CASE WHEN LOWER(a.title) LIKE '%assignment%' THEN 1 ELSE 0 END), 0), 2)
            AS assignment_submission_rate,
        ROUND(AVG(m.marks_obtained / NULLIF(a.max_marks, 0) * 100), 2) AS current_marks_pct
    FROM enrollment e
    JOIN course_offering co ON co.offering_id = e.offering_id
    JOIN term_window tw ON tw.term_id = co.term_id
    JOIN assessment a
        ON a.offering_id = co.offering_id
       AND a.assessment_date <= tw.midterm_date
    LEFT JOIN marks m
        ON m.enrollment_id = e.enrollment_id
       AND m.offering_id = a.offering_id
       AND m.assessment_no = a.assessment_no
    GROUP BY e.student_id, co.term_id
),
previous_performance AS (
    SELECT
        st.student_id,
        st.term_id,
        ROUND(AVG(CASE WHEN prev_t.end_date < st.start_date THEN gs.grade_point END), 2) AS cgpa_so_far,
        SUM(CASE WHEN prev_t.end_date < st.start_date AND e.grade = 'F' THEN 1 ELSE 0 END) AS backlog_count,
        ROUND(AVG(CASE WHEN prev_t.end_date < st.start_date
                      THEN m.marks_obtained / NULLIF(a.max_marks, 0) * 100 END), 2) AS previous_marks_pct
    FROM student_terms st
    LEFT JOIN enrollment e ON e.student_id = st.student_id
    LEFT JOIN course_offering co ON co.offering_id = e.offering_id
    LEFT JOIN term prev_t ON prev_t.term_id = co.term_id
    LEFT JOIN grade_scale gs ON gs.grade_letter = e.grade
    LEFT JOIN assessment a ON a.offering_id = co.offering_id
    LEFT JOIN marks m
        ON m.enrollment_id = e.enrollment_id
       AND m.offering_id = a.offering_id
       AND m.assessment_no = a.assessment_no
    GROUP BY st.student_id, st.term_id
),
prior_term_gpa AS (
    SELECT
        st.student_id,
        st.term_id,
        ROUND(AVG(gs.grade_point), 2) AS prev_term_gpa
    FROM student_terms st
    JOIN enrollment e ON e.student_id = st.student_id
    JOIN course_offering co ON co.offering_id = e.offering_id
    JOIN term prior_t ON prior_t.term_id = co.term_id
    LEFT JOIN grade_scale gs ON gs.grade_letter = e.grade
    WHERE prior_t.end_date = (
        SELECT MAX(previous_t.end_date)
        FROM enrollment previous_e
        JOIN course_offering previous_co ON previous_co.offering_id = previous_e.offering_id
        JOIN term previous_t ON previous_t.term_id = previous_co.term_id
        WHERE previous_e.student_id = st.student_id
          AND previous_t.end_date < st.start_date
    )
    GROUP BY st.student_id, st.term_id
),
fee_features AS (
    SELECT
        st.student_id,
        st.term_id,
        CASE
            WHEN SUM(CASE WHEN fp.status = 'Pending' THEN 1 ELSE 0 END) > 0
            THEN DATEDIFF(st.midterm_date, MIN(st.start_date))
            ELSE 0
        END AS fee_delay_days
    FROM student_terms st
    LEFT JOIN fee_payment fp
        ON fp.student_id = st.student_id
       AND (fp.term_id = st.term_id OR fp.term_id IS NULL)
    GROUP BY st.student_id, st.term_id, st.midterm_date
)
SELECT
    st.student_id,
    st.term_id,
    st.start_date AS term_start_date,
    st.program_id,
    st.year_of_study,
    COALESCE(att.attendance_pct_midterm, 100.00) AS attendance_pct_midterm,
    am.internal1_avg_pct,
    am.internal2_avg_pct,
    COALESCE(am.assignment_submission_rate, 100.00) AS assignment_submission_rate,
    pt.prev_term_gpa,
    pp.cgpa_so_far,
    COALESCE(pp.backlog_count, 0) AS backlog_count,
    COALESCE(ff.fee_delay_days, 0) AS fee_delay_days,
    COALESCE((
        SELECT SUM(sub.credits)
        FROM enrollment e2
        JOIN course_offering co2 ON co2.offering_id = e2.offering_id
        JOIN subject sub ON sub.subject_id = co2.subject_id
        WHERE e2.student_id = st.student_id AND co2.term_id = st.term_id
    ), 0) AS credits_this_term,
    ROUND(COALESCE(am.current_marks_pct, 0) - COALESCE(pp.previous_marks_pct, am.current_marks_pct, 0), 2)
        AS marks_trend,
    CASE WHEN st.year_of_study = 1 THEN TRUE ELSE FALSE END AS is_first_year
FROM student_terms st
LEFT JOIN attendance_midterm att
    ON att.student_id = st.student_id AND att.term_id = st.term_id
LEFT JOIN assessment_midterm am
    ON am.student_id = st.student_id AND am.term_id = st.term_id
LEFT JOIN previous_performance pp
    ON pp.student_id = st.student_id AND pp.term_id = st.term_id
LEFT JOIN prior_term_gpa pt
    ON pt.student_id = st.student_id AND pt.term_id = st.term_id
LEFT JOIN fee_features ff
    ON ff.student_id = st.student_id AND ff.term_id = st.term_id;

CREATE OR REPLACE VIEW v_ml_labels AS
SELECT
    e.student_id,
    co.term_id,
    MAX(CASE WHEN e.grade = 'F' OR e.is_detained = TRUE THEN 1 ELSE 0 END) AS at_risk
FROM enrollment e
JOIN course_offering co ON co.offering_id = e.offering_id
GROUP BY e.student_id, co.term_id;

GRANT SELECT ON edu_db.v_ml_features TO 'ml_readonly_role';
GRANT SELECT ON edu_db.v_ml_labels TO 'ml_readonly_role';
