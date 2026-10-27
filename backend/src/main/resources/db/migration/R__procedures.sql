-- =============================================================================
-- R__procedures.sql
-- Repeatable Flyway Migration: Stored Procedures (Academic Workflows & Concurrency)
-- Database: edu_db | Flyway User: flyway_user
--
-- DBMS Concepts Demonstrated:
--   1. Transaction Isolation & Explicit Row-Level Locking:
--      - 'sp_enroll_student' executes 'SELECT ... FOR UPDATE' on Course_Offering
--        to serialize concurrent enrollment requests and prevent overbooking (last-seat race).
--   2. Exception Handling & Atomicity:
--      - Uses 'DECLARE EXIT HANDLER FOR SQLEXCEPTION' to automatically ROLLBACK
--        uncommitted operations and RESIGNAL the exact SQLSTATE/message to caller.
--   3. Procedural Cursors & Iterative Evaluation:
--      - 'sp_compute_final_grades' iterates over student enrollments to aggregate
--        weighted assessment components and execute soft lookups against Grade_Scale.
--   4. Batch Closure Transactions:
--      - 'sp_term_closure' performs an atomic multi-table state transition.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. sp_enroll_student(p_student_id, p_offering_id)
-- Atomically enrolls a student into a course offering.
-- Employs SELECT ... FOR UPDATE to acquire an exclusive row lock on the offering,
-- guaranteeing that concurrent threads do not overbook the last available seat.
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_enroll_student;

DELIMITER $$
CREATE PROCEDURE sp_enroll_student(
    IN p_student_id INT,
    IN p_offering_id INT
)
BEGIN
    DECLARE v_offering_id INT;
    DECLARE v_capacity INT;
    DECLARE v_status VARCHAR(20);
    DECLARE v_already_enrolled INT DEFAULT 0;

    -- Exit handler: rollback and re-throw exception if any constraint/trigger fails
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    -- Acquire an exclusive row lock (FOR UPDATE) on the course_offering row
    SELECT offering_id, capacity, status
    INTO v_offering_id, v_capacity, v_status
    FROM course_offering
    WHERE offering_id = p_offering_id
    FOR UPDATE;

    -- Verify that the offering exists
    IF v_offering_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Enrollment failed: Course offering does not exist.';
    END IF;

    -- Verify offering status
    IF v_status <> 'Open' THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Enrollment failed: Course offering is not currently open for registration.';
    END IF;

    -- Verify student is not already actively enrolled
    SELECT COUNT(*) INTO v_already_enrolled
    FROM enrollment
    WHERE student_id = p_student_id
      AND offering_id = p_offering_id
      AND status <> 'Dropped';

    IF v_already_enrolled > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Enrollment failed: Student is already registered in this course offering.';
    END IF;

    -- Insert new enrollment.
    -- The BEFORE INSERT trigger (trg_enrollment_before_insert) will fire here
    -- to validate capacity, the 26-credit limit, and passing prerequisites.
    INSERT INTO enrollment (
        student_id,
        offering_id,
        enroll_date,
        status,
        final_marks,
        grade,
        is_detained
    ) VALUES (
        p_student_id,
        p_offering_id,
        CURDATE(),
        'Enrolled',
        NULL,
        NULL,
        FALSE
    );

    COMMIT;
END$$
DELIMITER ;


-- -----------------------------------------------------------------------------
-- 2. sp_drop_enrollment(p_student_id, p_offering_id)
-- Drops an active course enrollment, setting status to 'Dropped'.
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_drop_enrollment;

DELIMITER $$
CREATE PROCEDURE sp_drop_enrollment(
    IN p_student_id INT,
    IN p_offering_id INT
)
BEGIN
    DECLARE v_rows_affected INT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    -- Update active enrollment to 'Dropped'
    UPDATE enrollment
    SET status = 'Dropped'
    WHERE student_id = p_student_id
      AND offering_id = p_offering_id
      AND status = 'Enrolled';

    SET v_rows_affected = ROW_COUNT();

    IF v_rows_affected = 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Drop failed: No active enrollment found for this student and course offering.';
    END IF;

    COMMIT;
END$$
DELIMITER ;


-- -----------------------------------------------------------------------------
-- 3. sp_compute_final_grades(p_offering_id)
-- Computes the weighted percentage score for every enrolled student in an offering
-- by iterating via a cursor, looking up Grade_Scale, and persisting final_marks and grade.
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_compute_final_grades;

DELIMITER $$
CREATE PROCEDURE sp_compute_final_grades(
    IN p_offering_id INT
)
BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_enrollment_id INT;
    DECLARE v_total_weighted DECIMAL(6,2);
    DECLARE v_grade_letter VARCHAR(2);

    -- Cursor iterating through all non-dropped enrollments in the offering
    DECLARE cur_enrollments CURSOR FOR
        SELECT enrollment_id
        FROM enrollment
        WHERE offering_id = p_offering_id
          AND status <> 'Dropped';

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    OPEN cur_enrollments;

    grade_loop: LOOP
        FETCH cur_enrollments INTO v_enrollment_id;
        IF v_done = 1 THEN
            LEAVE grade_loop;
        END IF;

        -- Sum weighted marks: SUM((marks_obtained / max_marks) * weightage)
        SELECT 
            COALESCE(SUM((m.marks_obtained / a.max_marks) * a.weightage), 0.0)
        INTO v_total_weighted
        FROM marks m
        JOIN assessment a 
          ON m.offering_id = a.offering_id 
         AND m.assessment_no = a.assessment_no
        WHERE m.enrollment_id = v_enrollment_id
          AND m.offering_id = p_offering_id;

        -- Lookup corresponding letter grade from grade_scale table
        SELECT grade_letter INTO v_grade_letter
        FROM grade_scale
        WHERE v_total_weighted >= min_percent 
          AND v_total_weighted <= max_percent
        LIMIT 1;

        IF v_grade_letter IS NULL THEN
            SET v_grade_letter = 'F';
        END IF;

        -- Update enrollment record with finalized marks and grade
        UPDATE enrollment
        SET final_marks = v_total_weighted,
            grade = v_grade_letter
        WHERE enrollment_id = v_enrollment_id;

    END LOOP grade_loop;

    CLOSE cur_enrollments;

    COMMIT;
END$$
DELIMITER ;


-- -----------------------------------------------------------------------------
-- 4. sp_generate_transcript(p_student_id)
-- Generates and returns a comprehensive student transcript result set,
-- including course grades, credit points, and overall CGPA.
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_generate_transcript;

DELIMITER $$
CREATE PROCEDURE sp_generate_transcript(
    IN p_student_id INT
)
BEGIN
    -- Return detailed course records from the pre-joined transcript view
    SELECT 
        student_id,
        roll_no,
        student_name,
        program_name,
        term_id,
        term_name,
        subject_code,
        subject_title,
        credits,
        instructor_name,
        final_marks,
        grade,
        grade_point,
        quality_points
    FROM v_student_transcript
    WHERE student_id = p_student_id
    ORDER BY term_start_date ASC, subject_code ASC;

    -- Return summary metadata (total credits earned, overall CGPA)
    SELECT 
        s.student_id,
        s.roll_no,
        CONCAT(p.first_name, ' ', p.last_name) AS student_name,
        prog.name AS program_name,
        fn_cgpa(s.student_id) AS cumulative_gpa,
        (
            SELECT COALESCE(SUM(sub.credits), 0)
            FROM enrollment e
            JOIN course_offering co ON e.offering_id = co.offering_id
            JOIN subject sub ON co.subject_id = sub.subject_id
            WHERE e.student_id = s.student_id
              AND e.final_marks IS NOT NULL
              AND e.grade <> 'F'
        ) AS total_credits_earned
    FROM student s
    JOIN person p ON s.student_id = p.person_id
    JOIN program prog ON s.program_id = prog.program_id
    WHERE s.student_id = p_student_id;
END$$
DELIMITER ;


-- -----------------------------------------------------------------------------
-- 5. sp_term_closure(p_term_id)
-- Executes institutional end-of-term closure:
--   1. Computes final grades for all offerings in the term.
--   2. Updates offering statuses to 'Completed'.
--   3. Updates all active enrollments to 'Completed'.
--   4. Sets term.is_current to FALSE.
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_term_closure;

DELIMITER $$
CREATE PROCEDURE sp_term_closure(
    IN p_term_id INT
)
BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_offering_id INT;

    -- Cursor over all offerings in the given term
    DECLARE cur_offerings CURSOR FOR
        SELECT offering_id
        FROM course_offering
        WHERE term_id = p_term_id;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    -- 1. Compute final grades for each offering in the term
    OPEN cur_offerings;

    offering_loop: LOOP
        FETCH cur_offerings INTO v_offering_id;
        IF v_done = 1 THEN
            LEAVE offering_loop;
        END IF;

        -- Update final grades for this offering
        UPDATE enrollment e
        JOIN (
            SELECT 
                m.enrollment_id,
                COALESCE(SUM((m.marks_obtained / a.max_marks) * a.weightage), 0.0) AS calc_marks
            FROM marks m
            JOIN assessment a 
              ON m.offering_id = a.offering_id 
             AND m.assessment_no = a.assessment_no
            WHERE m.offering_id = v_offering_id
            GROUP BY m.enrollment_id
        ) m_agg ON e.enrollment_id = m_agg.enrollment_id
        JOIN grade_scale gs 
          ON m_agg.calc_marks >= gs.min_percent 
         AND m_agg.calc_marks <= gs.max_percent
        SET e.final_marks = m_agg.calc_marks,
            e.grade = gs.grade_letter
        WHERE e.offering_id = v_offering_id
          AND e.status <> 'Dropped';

    END LOOP offering_loop;

    CLOSE cur_offerings;

    -- 2. Transition all active course offerings to 'Completed'
    UPDATE course_offering
    SET status = 'Completed'
    WHERE term_id = p_term_id;

    -- 3. Transition all active enrollments to 'Completed'
    UPDATE enrollment
    SET status = 'Completed'
    WHERE offering_id IN (
        SELECT offering_id FROM course_offering WHERE term_id = p_term_id
    )
    AND status = 'Enrolled';

    -- 4. Mark the term as closed (not current)
    UPDATE term
    SET is_current = FALSE
    WHERE term_id = p_term_id;

    COMMIT;
END$$
DELIMITER ;
