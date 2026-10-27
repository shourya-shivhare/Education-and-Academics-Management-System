-- =============================================================================
-- q19.sql
-- Concept: Relational Set Operations via Join Equivalents (MySQL 8.x Compatible)
-- Business Question: Find students who enrolled in Course A but NOT Course B (Set Difference / EXCEPT),
--                    along with students enrolled in BOTH courses (Set Intersection / INTERSECT).
--
-- DBMS Concept Note:
-- Native INTERSECT and EXCEPT were added in MySQL 8.0.31+. To maintain universal compatibility
-- across all MySQL 8.x installations (including earlier 8.0.x minor versions), set difference
-- is implemented via LEFT JOIN / NOT EXISTS (anti-join), and set intersection is implemented
-- via dual INNER JOINs.
-- =============================================================================

-- Set Difference (A EXCEPT B): Enrolled in Course A but NOT Course B
SELECT 
    'Enrolled in A but NOT B (Difference)' AS set_relation,
    s.student_id,
    s.roll_no,
    CONCAT(p.first_name, ' ', p.last_name) AS student_name
FROM student s
JOIN person p ON s.student_id = p.person_id
JOIN enrollment e_a ON s.student_id = e_a.student_id
JOIN course_offering co_a ON e_a.offering_id = co_a.offering_id
JOIN subject sub_a ON co_a.subject_id = sub_a.subject_id
WHERE sub_a.code = ?
  AND NOT EXISTS (
      SELECT 1 
      FROM enrollment e_b
      JOIN course_offering co_b ON e_b.offering_id = co_b.offering_id
      JOIN subject sub_b ON co_b.subject_id = sub_b.subject_id
      WHERE e_b.student_id = s.student_id
        AND sub_b.code = ?
  )

UNION ALL

-- Set Intersection (A INTERSECT B): Enrolled in BOTH Course A and Course B
SELECT 
    'Enrolled in Both A and B (Intersection)' AS set_relation,
    s.student_id,
    s.roll_no,
    CONCAT(p.first_name, ' ', p.last_name) AS student_name
FROM student s
JOIN person p ON s.student_id = p.person_id
JOIN enrollment e_a ON s.student_id = e_a.student_id
JOIN course_offering co_a ON e_a.offering_id = co_a.offering_id
JOIN subject sub_a ON co_a.subject_id = sub_a.subject_id
JOIN enrollment e_b ON s.student_id = e_b.student_id
JOIN course_offering co_b ON e_b.offering_id = co_b.offering_id
JOIN subject sub_b ON co_b.subject_id = sub_b.subject_id
WHERE sub_a.code = ?
  AND sub_b.code = ?;
