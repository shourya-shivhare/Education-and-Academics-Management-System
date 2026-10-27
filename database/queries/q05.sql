-- =============================================================================
-- q05.sql
-- Concept: SELF JOIN
-- Business Question: For every subject with prerequisites, what is the subject's title,
--                    its required prerequisite subject's title, and the minimum passing grade?
--
-- DBMS Concept Note:
-- A self-join joins two instances of the same relation ('subject') by aliasing
-- them differently ('s' and 'prereq'). In our normalized schema, the unary recursive
-- M:N relationship is resolved through the junction table 'subject_prerequisite'.
-- =============================================================================

SELECT 
    s.code AS course_code,
    s.title AS course_title,
    sp.min_grade AS required_min_grade,
    prereq.code AS prerequisite_code,
    prereq.title AS prerequisite_title
FROM subject s
JOIN subject_prerequisite sp ON s.subject_id = sp.subject_id
JOIN subject prereq ON sp.prereq_subject_id = prereq.subject_id
ORDER BY s.code, prereq.code;
