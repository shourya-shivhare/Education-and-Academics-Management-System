-- =============================================================================
-- V2__academic_tables.sql - academic/operational entities (Flyway, flyway_user, edu_db)
-- Course_Offering, Timetable_Slot, Enrollment, Attendance, Assessment, Marks,
-- Fee_Payment, Users, Audit_Log, Student_Risk. Tables are ordered so every FK
-- target already exists.
-- =============================================================================

CREATE TABLE course_offering (
    offering_id INT         NOT NULL AUTO_INCREMENT,
    subject_id  INT         NOT NULL,
    faculty_id  INT         NOT NULL,
    term_id     INT         NOT NULL,
    section     VARCHAR(5)  NOT NULL,
    capacity    INT         NOT NULL,
    status      ENUM('Open','Closed','Cancelled','Completed') NOT NULL DEFAULT 'Open',
    CONSTRAINT pk_course_offering PRIMARY KEY (offering_id),
    CONSTRAINT uq_course_offering_natural UNIQUE (subject_id, faculty_id, term_id, section),
    CONSTRAINT chk_course_offering_capacity CHECK (capacity > 0),
    CONSTRAINT fk_course_offering_subject FOREIGN KEY (subject_id)
        REFERENCES subject(subject_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_course_offering_faculty FOREIGN KEY (faculty_id)
        REFERENCES faculty(faculty_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_course_offering_term FOREIGN KEY (term_id)
        REFERENCES term(term_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- enrolled_count is derived from enrollment and is NOT stored.

CREATE TABLE timetable_slot (
    slot_id     INT         NOT NULL AUTO_INCREMENT,
    offering_id INT         NOT NULL,
    room_id     INT         NOT NULL,
    day_of_week ENUM('Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday') NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    CONSTRAINT pk_timetable_slot PRIMARY KEY (slot_id),
    CONSTRAINT chk_timetable_slot_timeorder CHECK (end_time > start_time),
    CONSTRAINT fk_timetable_slot_offering FOREIGN KEY (offering_id)
        REFERENCES course_offering(offering_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_timetable_slot_classroom FOREIGN KEY (room_id)
        REFERENCES classroom(room_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- The no-faculty/no-room double-booking rule (overlap on same day) is enforced by a
-- trigger, NOT a key/CHECK, because a time-overlap predicate cannot be expressed as uniqueness.

CREATE TABLE enrollment (
    enrollment_id INT          NOT NULL AUTO_INCREMENT,
    student_id    INT          NOT NULL,
    offering_id   INT          NOT NULL,
    enroll_date   DATE         NOT NULL,
    status        ENUM('Enrolled','Dropped','Completed') NOT NULL DEFAULT 'Enrolled',
    final_marks   DECIMAL(6,2) NULL,
    grade         VARCHAR(2)   NULL,
    CONSTRAINT pk_enrollment PRIMARY KEY (enrollment_id),
    CONSTRAINT uq_enrollment_student_offering UNIQUE (student_id, offering_id),
    CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id)
        REFERENCES student(student_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_enrollment_offering FOREIGN KEY (offering_id)
        REFERENCES course_offering(offering_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Only final_marks + grade are stored derived values. is_detained is NOT stored
-- (the 75% detention check is computed by a view/procedure at exam-eligibility time).
-- age / attendance_percent / cgpa / grade_letter / enrolled_count are never stored.

CREATE TABLE attendance (
    attendance_id INT          NOT NULL AUTO_INCREMENT,
    enrollment_id INT          NOT NULL,
    class_date    DATE         NOT NULL,
    status        ENUM('Present','Absent','Late') NOT NULL,
    CONSTRAINT pk_attendance PRIMARY KEY (attendance_id),
    CONSTRAINT uq_attendance_enrollment_date UNIQUE (enrollment_id, class_date),
    CONSTRAINT fk_attendance_enrollment FOREIGN KEY (enrollment_id)
        REFERENCES enrollment(enrollment_id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- attendance_percent is derived per enrollment and is NOT stored.

CREATE TABLE assessment (
    offering_id     INT          NOT NULL,
    assessment_no   INT          NOT NULL,
    title           VARCHAR(100) NOT NULL,
    max_marks       INT          NOT NULL,
    weightage       INT          NOT NULL,
    assessment_date DATE         NULL,
    CONSTRAINT pk_assessment PRIMARY KEY (offering_id, assessment_no),
    CONSTRAINT chk_assessment_weightage CHECK (weightage BETWEEN 0 AND 100),
    CONSTRAINT chk_assessment_max_marks CHECK (max_marks > 0),
    CONSTRAINT fk_assessment_offering FOREIGN KEY (offering_id)
        REFERENCES course_offering(offering_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Weak entity: PK = (offering_id, assessment_no). The sum-of-weightage <= 100 rule
-- (a cross-row aggregate) is enforced by a trigger, not a CHECK.

CREATE TABLE marks (
    enrollment_id  INT          NOT NULL,
    offering_id    INT          NOT NULL,
    assessment_no  INT          NOT NULL,
    marks_obtained DECIMAL(6,2) NOT NULL,
    version        INT          NOT NULL DEFAULT 1,
    CONSTRAINT pk_marks PRIMARY KEY (enrollment_id, offering_id, assessment_no),
    CONSTRAINT chk_marks_obtained CHECK (marks_obtained >= 0),
    CONSTRAINT fk_marks_enrollment FOREIGN KEY (enrollment_id)
        REFERENCES enrollment(enrollment_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_marks_assessment FOREIGN KEY (offering_id, assessment_no)
        REFERENCES assessment(offering_id, assessment_no)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- grade_letter is derived via grade_scale and is NOT stored.
-- marks_obtained <= max_marks is enforced by a trigger (cross-table comparison).

CREATE TABLE fee_payment (
    payment_id    INT           NOT NULL AUTO_INCREMENT,
    student_id    INT           NOT NULL,
    term_id       INT           NULL,
    amount        DECIMAL(10,2) NOT NULL,
    payment_date  DATE          NOT NULL,
    payment_mode  ENUM('Cash','Card','Online','Cheque') NOT NULL,
    status        ENUM('Paid','Pending','Failed') NOT NULL DEFAULT 'Pending',
    CONSTRAINT pk_fee_payment PRIMARY KEY (payment_id),
    CONSTRAINT chk_fee_payment_amount CHECK (amount >= 0),
    CONSTRAINT fk_fee_payment_student FOREIGN KEY (student_id)
        REFERENCES student(student_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_fee_payment_term FOREIGN KEY (term_id)
        REFERENCES term(term_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE users (
    user_id       INT          NOT NULL AUTO_INCREMENT,
    person_id     INT          NULL,
    username      VARCHAR(60)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    `role`        ENUM('Admin','Faculty','Student') NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login    DATETIME     NULL,
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT fk_users_person FOREIGN KEY (person_id)
        REFERENCES person(person_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- `role` is backticked because ROLE is a reserved word in MySQL 8.

CREATE TABLE audit_log (
    log_id      BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     INT          NULL,
    action      ENUM('INSERT','UPDATE','DELETE') NOT NULL,
    table_name  VARCHAR(60)  NOT NULL,
    row_pk      VARCHAR(60)  NULL,
    old_values  JSON         NULL,
    new_values  JSON         NULL,
    timestamp   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit_log PRIMARY KEY (log_id),
    CONSTRAINT fk_audit_log_user FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE student_risk (
    risk_id             INT           NOT NULL AUTO_INCREMENT,
    student_id          INT           NOT NULL,
    term_id             INT           NULL,
    risk_score          DECIMAL(5,2)  NOT NULL,
    risk_label          ENUM('Low','Medium','High') NOT NULL,
    contributing_factors JSON          NULL,
    computed_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_student_risk PRIMARY KEY (risk_id),
    CONSTRAINT chk_student_risk_score CHECK (risk_score BETWEEN 0 AND 100),
    CONSTRAINT fk_student_risk_student FOREIGN KEY (student_id)
        REFERENCES student(student_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_student_risk_term FOREIGN KEY (term_id)
        REFERENCES term(term_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Written by the Spring backend from the ai-service ML output.
