-- =============================================================================
-- q01.sql
-- Concept: Multi-table INNER JOIN (5+ tables)
-- Business Question: What is the comprehensive academic transcript for a student,
--                    including course code, title, credits, term, instructor, and grade?
--
-- DBMS Concept Note:
-- Demonstrates multi-way equijoins across normalized tables (student, person,
-- enrollment, course_offering, term, subject, faculty). The optimizer evaluates
-- join order using table cardinality and index statistics (e.g., student_id PK,
-- offering_id FK) to minimize intermediate Cartesian products.
-- =============================================================================

SELECT 
    s.student_id,
    s.roll_no,
    CONCAT(p_stu.first_name, ' ', p_stu.last_name) AS student_name,
    t.name AS term_name,
    sub.code AS subject_code,
    sub.title AS subject_title,
    sub.credits,
    CONCAT(p_fac.first_name, ' ', p_fac.last_name) AS instructor_name,
    e.final_marks,
    e.grade
FROM student s
JOIN person p_stu ON s.student_id = p_stu.person_id
JOIN enrollment e ON s.student_id = e.student_id
JOIN course_offering co ON e.offering_id = co.offering_id
JOIN term t ON co.term_id = t.term_id
JOIN subject sub ON co.subject_id = sub.subject_id
JOIN faculty f ON co.faculty_id = f.faculty_id
JOIN person p_fac ON f.faculty_id = p_fac.person_id
WHERE s.student_id = ?
ORDER BY t.start_date, sub.code;
