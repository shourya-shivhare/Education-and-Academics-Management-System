-- =============================================================================
-- R__triggers.sql
-- Repeatable Flyway Migration: Database Triggers (Business Rule Enforcement)
-- Database: edu_db | Flyway User: flyway_user
--
-- DBMS Principle Demonstrated:
--   "Business rules live in MySQL (constraints, triggers, procedures, views, roles).
--    Java only calls them."
--
-- All violation errors use:
--   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '...';
-- Translates into HTTP 409 Conflict in the Spring Boot ControllerAdvice.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. trg_enrollment_before_insert
-- Enforces:
--   a) Course offering capacity limit.
--   b) Student 26-credit ceiling per academic term.
--   c) Prerequisite completion with a passing grade (grade != 'F').
-- -----------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_enrollment_before_insert;

DELIMITER $$
CREATE TRIGGER trg_enrollment_before_insert
BEFORE INSERT ON enrollment
FOR EACH ROW
BEGIN
    DECLARE v_term_id INT;
    DECLARE v_subject_id INT;
    DECLARE v_sub_credits INT;
    DECLARE v_capacity INT;
    DECLARE v_current_enrolled INT DEFAULT 0;
    DECLARE v_term_credits INT DEFAULT 0;
    DECLARE v_missing_prereqs INT DEFAULT 0;

    -- Retrieve offering metadata: term, subject, capacity, and credits
    SELECT co.term_id, co.subject_id, co.capacity, s.credits
    INTO v_term_id, v_subject_id, v_capacity, v_sub_credits
    FROM course_offering co
    JOIN subject s ON co.subject_id = s.subject_id
    WHERE co.offering_id = NEW.offering_id;

    -- RULE 1: Capacity check
    SELECT COUNT(*) INTO v_current_enrolled
    FROM enrollment
    WHERE offering_id = NEW.offering_id
      AND status != 'Dropped';

    IF v_current_enrolled >= v_capacity THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Enrollment rejected: Course offering has reached its maximum student capacity.';
    END IF;

    -- RULE 2: Maximum 26 credits per student per term
    SELECT COALESCE(SUM(s.credits), 0) INTO v_term_credits
    FROM enrollment e
    JOIN course_offering co ON e.offering_id = co.offering_id
    JOIN subject s ON co.subject_id = s.subject_id
    WHERE e.student_id = NEW.student_id
      AND co.term_id = v_term_id
      AND e.status != 'Dropped';

    IF (v_term_credits + v_sub_credits) > 26 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Enrollment rejected: Enrollment exceeds maximum 26 credits allowed per academic term.';
    END IF;

    -- RULE 3: Prerequisite completion with passing grade (grade != 'F' and grade IS NOT NULL)
    SELECT COUNT(*) INTO v_missing_prereqs
    FROM subject_prerequisite sp
    WHERE sp.subject_id = v_subject_id
      AND NOT EXISTS (
          SELECT 1 
          FROM enrollment e_prev
          JOIN course_offering co_prev ON e_prev.offering_id = co_prev.offering_id
          WHERE e_prev.student_id = NEW.student_id
            AND co_prev.subject_id = sp.prereq_subject_id
            AND e_prev.status = 'Completed'
            AND e_prev.grade IS NOT NULL
            AND e_prev.grade != 'F'
      );

    IF v_missing_prereqs > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Enrollment rejected: Student has not satisfied all prerequisite courses with a passing grade.';
    END IF;
END$$
DELIMITER ;


-- -----------------------------------------------------------------------------
-- 2. trg_timetable_slot_before_insert
-- Enforces:
--   a) Classroom double-booking prevention in the same term and time window.
--   b) Faculty double-booking prevention in the same term and time window.
-- -----------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_timetable_slot_before_insert;

DELIMITER $$
CREATE TRIGGER trg_timetable_slot_before_insert
BEFORE INSERT ON timetable_slot
FOR EACH ROW
BEGIN
    DECLARE v_term_id INT;
    DECLARE v_faculty_id INT;
    DECLARE v_room_clash INT DEFAULT 0;
    DECLARE v_fac_clash INT DEFAULT 0;

    -- Retrieve offering's term and instructor
    SELECT term_id, faculty_id
    INTO v_term_id, v_faculty_id
    FROM course_offering
    WHERE offering_id = NEW.offering_id;

    -- Check 1: Classroom clash (Interval overlap: start_time < existing.end AND end_time > existing.start)
    SELECT COUNT(*) INTO v_room_clash
    FROM timetable_slot ts
    JOIN course_offering co ON ts.offering_id = co.offering_id
    WHERE co.term_id = v_term_id
      AND ts.room_id = NEW.room_id
      AND ts.day_of_week = NEW.day_of_week
      AND NEW.start_time < ts.end_time 
      AND NEW.end_time > ts.start_time;

    IF v_room_clash > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Timetable slot clash: The selected classroom is already occupied during this time window.';
    END IF;

    -- Check 2: Faculty clash
    SELECT COUNT(*) INTO v_fac_clash
    FROM timetable_slot ts
    JOIN course_offering co ON ts.offering_id = co.offering_id
    WHERE co.term_id = v_term_id
      AND co.faculty_id = v_faculty_id
      AND ts.day_of_week = NEW.day_of_week
      AND NEW.start_time < ts.end_time 
      AND NEW.end_time > ts.start_time;

    IF v_fac_clash > 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Timetable slot clash: The instructor is already scheduled for another course during this time window.';
    END IF;
END$$
DELIMITER ;


-- -----------------------------------------------------------------------------
-- 3. trg_marks_before_insert
-- Enforces marks bounds: 0 <= marks_obtained <= assessment.max_marks.
-- -----------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_marks_before_insert;

DELIMITER $$
CREATE TRIGGER trg_marks_before_insert
BEFORE INSERT ON marks
FOR EACH ROW
BEGIN
    DECLARE v_max_marks INT;

    SELECT max_marks INTO v_max_marks
    FROM assessment
    WHERE offering_id = NEW.offering_id 
      AND assessment_no = NEW.assessment_no;

    IF NEW.marks_obtained < 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Invalid marks: Marks obtained cannot be negative.';
    END IF;

    IF NEW.marks_obtained > v_max_marks THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Invalid marks: Marks obtained cannot exceed the assessment maximum marks.';
    END IF;
END$$
DELIMITER ;


-- -----------------------------------------------------------------------------
-- 4. trg_marks_before_update
-- Enforces marks bounds on updates.
-- -----------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_marks_before_update;

DELIMITER $$
CREATE TRIGGER trg_marks_before_update
BEFORE UPDATE ON marks
FOR EACH ROW
BEGIN
    DECLARE v_max_marks INT;

    SELECT max_marks INTO v_max_marks
    FROM assessment
    WHERE offering_id = NEW.offering_id 
      AND assessment_no = NEW.assessment_no;

    IF NEW.marks_obtained < 0 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Invalid marks: Marks obtained cannot be negative.';
    END IF;

    IF NEW.marks_obtained > v_max_marks THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Invalid marks: Marks obtained cannot exceed the assessment maximum marks.';
    END IF;
END$$
DELIMITER ;


-- -----------------------------------------------------------------------------
-- 5. trg_assessment_before_insert
-- Enforces: Total weightage across all assessments for an offering cannot exceed 100%.
-- -----------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_assessment_before_insert;

DELIMITER $$
CREATE TRIGGER trg_assessment_before_insert
BEFORE INSERT ON assessment
FOR EACH ROW
BEGIN
    DECLARE v_current_weightage INT DEFAULT 0;

    SELECT COALESCE(SUM(weightage), 0) INTO v_current_weightage
    FROM assessment
    WHERE offering_id = NEW.offering_id;

    IF (v_current_weightage + NEW.weightage) > 100 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Assessment weightage violation: Total weightage for course offering cannot exceed 100%.';
    END IF;
END$$
DELIMITER ;


-- -----------------------------------------------------------------------------
-- 6. trg_assessment_before_update
-- Enforces total weightage <= 100% on updates.
-- -----------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_assessment_before_update;

DELIMITER $$
CREATE TRIGGER trg_assessment_before_update
BEFORE UPDATE ON assessment
FOR EACH ROW
BEGIN
    DECLARE v_other_weightage INT DEFAULT 0;

    SELECT COALESCE(SUM(weightage), 0) INTO v_other_weightage
    FROM assessment
    WHERE offering_id = NEW.offering_id
      AND assessment_no <> NEW.assessment_no;

    IF (v_other_weightage + NEW.weightage) > 100 THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Assessment weightage violation: Total weightage for course offering cannot exceed 100%.';
    END IF;
END$$
DELIMITER ;


-- -----------------------------------------------------------------------------
-- 7. trg_marks_after_update
-- Writes historical changes into audit_log (capturing OLD and NEW values as JSON).
-- -----------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_marks_after_update;

DELIMITER $$
CREATE TRIGGER trg_marks_after_update
AFTER UPDATE ON marks
FOR EACH ROW
BEGIN
    -- Log only when marks value actually changed
    IF OLD.marks_obtained <> NEW.marks_obtained THEN
        INSERT INTO audit_log (
            user_id,
            action,
            table_name,
            row_pk,
            old_values,
            new_values,
            timestamp
        ) VALUES (
            NULL,
            'UPDATE',
            'marks',
            CONCAT('enr:', OLD.enrollment_id, ';off:', OLD.offering_id, ';ass:', OLD.assessment_no),
            JSON_OBJECT('marks_obtained', OLD.marks_obtained, 'version', OLD.version),
            JSON_OBJECT('marks_obtained', NEW.marks_obtained, 'version', NEW.version),
            NOW()
        );
    END IF;
END$$
DELIMITER ;


-- -----------------------------------------------------------------------------
-- 8. trg_attendance_after_insert
-- Recomputes attendance percentage and sets Enrollment.is_detained flag.
-- (MySQL prevents a trigger from modifying its own table; modifying enrollment
-- from an attendance trigger is fully valid and supported).
-- -----------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_attendance_after_insert;

DELIMITER $$
CREATE TRIGGER trg_attendance_after_insert
AFTER INSERT ON attendance
FOR EACH ROW
BEGIN
    DECLARE v_held INT DEFAULT 0;
    DECLARE v_attended INT DEFAULT 0;
    DECLARE v_pct DECIMAL(5,2);

    -- Calculate updated attendance metrics for the enrollment
    SELECT 
        COUNT(*),
        COALESCE(SUM(CASE WHEN status IN ('Present', 'Late') THEN 1 ELSE 0 END), 0)
    INTO v_held, v_attended
    FROM attendance
    WHERE enrollment_id = NEW.enrollment_id;

    IF v_held > 0 THEN
        SET v_pct = (v_attended / v_held) * 100.0;
        
        -- Update is_detained flag in enrollment table if attendance drops below 75%
        UPDATE enrollment
        SET is_detained = (v_pct < 75.00)
        WHERE enrollment_id = NEW.enrollment_id;
    END IF;
END$$
DELIMITER ;


-- -----------------------------------------------------------------------------
-- 9. trg_student_before_delete
-- Blocks deletion of student if academic history (enrollments or fee payments)
-- exists, and logs the deletion attempt in audit_log.
-- -----------------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_student_before_delete;

DELIMITER $$
CREATE TRIGGER trg_student_before_delete
BEFORE DELETE ON student
FOR EACH ROW
BEGIN
    DECLARE v_enrollments_count INT DEFAULT 0;
    DECLARE v_fees_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_enrollments_count
    FROM enrollment
    WHERE student_id = OLD.student_id;

    SELECT COUNT(*) INTO v_fees_count
    FROM fee_payment
    WHERE student_id = OLD.student_id;

    IF (v_enrollments_count > 0 OR v_fees_count > 0) THEN
        -- Audit log recording the rejected deletion attempt
        INSERT INTO audit_log (
            user_id,
            action,
            table_name,
            row_pk,
            old_values,
            new_values,
            timestamp
        ) VALUES (
            NULL,
            'DELETE',
            'student',
            CAST(OLD.student_id AS CHAR),
            JSON_OBJECT(
                'roll_no', OLD.roll_no,
                'rejection_reason', 'Academic history exists',
                'enrollments_count', v_enrollments_count,
                'fees_count', v_fees_count
            ),
            NULL,
            NOW()
        );

        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Integrity violation: Cannot delete student with existing academic records (enrollments or fee payments). Deactivate or archive student instead.';
    END IF;
END$$
DELIMITER ;
