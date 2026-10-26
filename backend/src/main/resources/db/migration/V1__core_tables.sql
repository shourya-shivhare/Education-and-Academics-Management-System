-- =============================================================================
-- V1__core_tables.sql - core entities (run by Flyway as flyway_user on edu_db)
-- Department, Program, Person, Person_Phone, Student, Faculty, Subject,
-- Subject_Prerequisite, Term, Classroom, Grade_Scale.
-- Department.hod_faculty_id is intentionally omitted here (circular FK -> faculty)
-- and is added in V3 to break the creation-order cycle.
-- =============================================================================

CREATE TABLE department (
    department_id    INT          NOT NULL AUTO_INCREMENT,
    name             VARCHAR(100) NOT NULL,
    established_year INT          NOT NULL,
    CONSTRAINT pk_department PRIMARY KEY (department_id),
    CONSTRAINT uq_department_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE program (
    program_id     INT          NOT NULL AUTO_INCREMENT,
    name           VARCHAR(120) NOT NULL,
    degree         VARCHAR(20)  NOT NULL,
    duration_years INT          NOT NULL,
    department_id  INT          NOT NULL,
    total_credits  INT          NOT NULL,
    CONSTRAINT pk_program PRIMARY KEY (program_id),
    CONSTRAINT fk_program_department FOREIGN KEY (department_id)
        REFERENCES department(department_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE person (
    person_id     INT          NOT NULL AUTO_INCREMENT,
    first_name    VARCHAR(60)  NOT NULL,
    last_name     VARCHAR(60)  NOT NULL,
    email         VARCHAR(120) NOT NULL,
    date_of_birth DATE         NOT NULL,
    gender        ENUM('M','F','Other') NULL,
    address_line  VARCHAR(200) NULL,
    city          VARCHAR(60)  NULL,
    state         VARCHAR(60)  NULL,
    pin_code      VARCHAR(10)  NULL,
    CONSTRAINT pk_person PRIMARY KEY (person_id),
    CONSTRAINT uq_person_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- age is derived from date_of_birth and is NOT stored.

CREATE TABLE person_phone (
    person_id  INT          NOT NULL,
    phone_no   VARCHAR(15)  NOT NULL,
    phone_type ENUM('Mobile','Home','Work','Other') NULL,
    CONSTRAINT pk_person_phone PRIMARY KEY (person_id, phone_no),
    CONSTRAINT fk_person_phone_person FOREIGN KEY (person_id)
        REFERENCES person(person_id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE student (
    student_id       INT          NOT NULL,
    roll_no          VARCHAR(20)  NOT NULL,
    program_id       INT          NOT NULL,
    admission_date   DATE         NOT NULL,
    status           ENUM('Active','Graduated','Dropped','Suspended') NOT NULL DEFAULT 'Active',
    current_semester INT          NOT NULL,
    CONSTRAINT pk_student PRIMARY KEY (student_id),
    CONSTRAINT uq_student_roll_no UNIQUE (roll_no),
    CONSTRAINT chk_student_semester CHECK (current_semester > 0),
    CONSTRAINT fk_student_person FOREIGN KEY (student_id)
        REFERENCES person(person_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_student_program FOREIGN KEY (program_id)
        REFERENCES program(program_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- student_id is PK & FK -> person (IS-A); NOT auto-increment. cgpa is derived, NOT stored.

CREATE TABLE faculty (
    faculty_id    INT          NOT NULL,
    employee_no   VARCHAR(20)  NOT NULL,
    department_id INT          NOT NULL,
    designation   VARCHAR(40)  NULL,
    hire_date     DATE         NOT NULL,
    max_load      INT          NULL,
    CONSTRAINT pk_faculty PRIMARY KEY (faculty_id),
    CONSTRAINT uq_faculty_employee_no UNIQUE (employee_no),
    CONSTRAINT fk_faculty_person FOREIGN KEY (faculty_id)
        REFERENCES person(person_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_faculty_department FOREIGN KEY (department_id)
        REFERENCES department(department_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- faculty_id is PK & FK -> person (IS-A); NOT auto-increment.
-- department.hod_faculty_id -> faculty (the circular FK) is added in V3.

CREATE TABLE subject (
    subject_id    INT          NOT NULL AUTO_INCREMENT,
    code          VARCHAR(15)  NOT NULL,
    title         VARCHAR(120) NOT NULL,
    credits       INT          NOT NULL,
    department_id INT          NOT NULL,
    program_id    INT          NULL,
    semester      INT          NOT NULL,
    CONSTRAINT pk_subject PRIMARY KEY (subject_id),
    CONSTRAINT uq_subject_code UNIQUE (code),
    CONSTRAINT chk_subject_credits CHECK (credits BETWEEN 1 AND 6),
    CONSTRAINT fk_subject_department FOREIGN KEY (department_id)
        REFERENCES department(department_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_subject_program FOREIGN KEY (program_id)
        REFERENCES program(program_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE subject_prerequisite (
    subject_id        INT         NOT NULL,
    prereq_subject_id INT         NOT NULL,
    min_grade         VARCHAR(2)  NOT NULL,
    CONSTRAINT pk_subject_prerequisite PRIMARY KEY (subject_id, prereq_subject_id),
    CONSTRAINT chk_subject_prerequisite_selfref CHECK (subject_id <> prereq_subject_id),
    CONSTRAINT fk_subject_prerequisite_subject FOREIGN KEY (subject_id)
        REFERENCES subject(subject_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_subject_prerequisite_prereq FOREIGN KEY (prereq_subject_id)
        REFERENCES subject(subject_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE term (
    term_id    INT          NOT NULL AUTO_INCREMENT,
    name       VARCHAR(40)  NOT NULL,
    start_date DATE         NOT NULL,
    end_date   DATE         NOT NULL,
    is_current BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_term PRIMARY KEY (term_id),
    CONSTRAINT uq_term_name UNIQUE (name),
    CONSTRAINT chk_term_dates CHECK (end_date >= start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE classroom (
    room_id  INT          NOT NULL AUTO_INCREMENT,
    building VARCHAR(40)  NOT NULL,
    room_no  VARCHAR(10)  NOT NULL,
    capacity INT          NOT NULL,
    CONSTRAINT pk_classroom PRIMARY KEY (room_id),
    CONSTRAINT uq_classroom_building_room_no UNIQUE (building, room_no),
    CONSTRAINT chk_classroom_capacity CHECK (capacity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE grade_scale (
    grade_id     INT           NOT NULL AUTO_INCREMENT,
    grade_letter VARCHAR(2)    NOT NULL,
    min_percent  DECIMAL(5,2)  NOT NULL,
    max_percent  DECIMAL(5,2)  NOT NULL,
    grade_point  DECIMAL(3,1)  NOT NULL,
    is_pass      BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_grade_scale PRIMARY KEY (grade_id),
    CONSTRAINT uq_grade_scale_letter UNIQUE (grade_letter),
    CONSTRAINT chk_grade_scale_range CHECK (min_percent < max_percent),
    CONSTRAINT chk_grade_scale_bounds CHECK (min_percent >= 0 AND max_percent <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- grade_scale is a soft lookup (no hard FK from marks/enrollment).
