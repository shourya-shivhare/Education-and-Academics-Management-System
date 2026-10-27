-- =============================================================================
-- q03.sql
-- Concept: RIGHT OUTER JOIN
-- Business Question: What are all course offerings in a given term and their enrolled
--                    students, including newly scheduled offerings with zero students?
--
-- DBMS Concept Note:
-- Preserves all rows from the right relation ('course_offering') regardless of
-- whether matching records exist in the left relation ('enrollment'). This demonstrates
-- outer join semantics in relational algebra when monitoring under-enrolled sections.
-- =============================================================================

SELECT 
    co.offering_id,
    sub.code AS subject_code,
    sub.title AS subject_title,
    co.section,
    co.capacity,
    e.enrollment_id,
    s.roll_no,
    CONCAT(p.first_name, ' ', p.last_name) AS student_name,
    e.status AS enrollment_status
FROM enrollment e
JOIN student s ON e.student_id = s.student_id
JOIN person p ON s.student_id = p.person_id
RIGHT JOIN course_offering co ON e.offering_id = co.offering_id
JOIN subject sub ON co.subject_id = sub.subject_id
WHERE co.term_id = ?
ORDER BY co.offering_id, s.roll_no;
