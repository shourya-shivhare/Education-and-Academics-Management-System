-- =============================================================================
-- q18.sql
-- Concept: Recursive Common Table Expression (WITH RECURSIVE Prerequisite Chain)
-- Business Question: For a given advanced subject, what is its complete recursive
--                    chain of prerequisites across all dependency depths?
--
-- DBMS Concept Note:
-- A recursive CTE traverses graph/hierarchical structures in relational databases.
-- The anchor query locates the direct root dependencies; the recursive query repeatedly
-- joins against 'subject_prerequisite' until no further parent nodes exist.
-- =============================================================================

WITH RECURSIVE prereq_tree AS (
    -- Anchor member: direct prerequisites of target subject
    SELECT 
        sp.subject_id,
        s.code AS course_code,
        s.title AS course_title,
        sp.prereq_subject_id,
        p.code AS prereq_code,
        p.title AS prereq_title,
        sp.min_grade,
        1 AS depth_level
    FROM subject_prerequisite sp
    JOIN subject s ON sp.subject_id = s.subject_id
    JOIN subject p ON sp.prereq_subject_id = p.subject_id
    WHERE s.code = ?

    UNION ALL

    -- Recursive member: prerequisites of prerequisites
    SELECT 
        child.subject_id,
        child.code AS course_code,
        child.title AS course_title,
        sp2.prereq_subject_id,
        p2.code AS prereq_code,
        p2.title AS prereq_title,
        sp2.min_grade,
        pt.depth_level + 1 AS depth_level
    FROM prereq_tree pt
    JOIN subject child ON pt.prereq_subject_id = child.subject_id
    JOIN subject_prerequisite sp2 ON child.subject_id = sp2.subject_id
    JOIN subject p2 ON sp2.prereq_subject_id = p2.subject_id
)
SELECT 
    depth_level,
    course_code,
    course_title,
    prereq_code,
    prereq_title,
    min_grade
FROM prereq_tree
ORDER BY depth_level ASC, course_code ASC;
