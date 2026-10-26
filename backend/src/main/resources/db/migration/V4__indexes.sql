-- =============================================================================
-- V4__indexes.sql - secondary indexes for hot query paths.
--
-- RULE (do not duplicate FK indexes): InnoDB automatically creates an index on the
-- child side of every FOREIGN KEY (and reuses an existing index if one already has
-- the FK column as its leftmost prefix). We therefore do NOT add single-column indexes
-- on FK columns such as enrollment.student_id, enrollment.offering_id,
-- attendance.enrollment_id, fee_payment.student_id / term_id, or
-- course_offering.subject_id / faculty_id / term_id - those would duplicate the
-- indexes InnoDB already created. Only multi-column indexes that EXTEND a FK index
-- (add columns to its right) or genuinely new access paths are added below.
-- =============================================================================

-- The requested FULLTEXT index is on Subject(name, description). The subject table
-- has `title` (the subject's name) but no `description` column, so add one here first.
ALTER TABLE subject
    ADD COLUMN description TEXT NULL;

-- FULLTEXT (InnoDB) for searching subjects by title + description.
ALTER TABLE subject
    ADD FULLTEXT INDEX ft_subject (title, description);

-- Per-student-per-term payment lookup. Extends (does not duplicate) the FK auto-index
-- on fee_payment.student_id by adding term_id as a second column.
ALTER TABLE fee_payment
    ADD INDEX idx_fee_payment_student_term (student_id, term_id);

-- "What is offered for a subject in a term" lookup. subject_id is the leftmost column
-- of the existing UNIQUE(subject_id, faculty_id, term_id, section), but term_id is 3rd
-- there, so a (subject_id, term_id) predicate is NOT served by that unique key; this
-- composite is.
ALTER TABLE course_offering
    ADD INDEX idx_course_offering_subject_term (subject_id, term_id);

-- Attendance date scans. NOTE: the requested (offering_id, class_date) cannot be created
-- on attendance because attendance has no offering_id column - an offering is reached via
-- enrollment (attendance.enrollment_id -> enrollment.offering_id). The "attendance for an
-- offering on a date" query is already served by enrollment.offering_id (FK auto-index)
-- joined to attendance UNIQUE(enrollment_id, class_date). This index adds the missing
-- access path: class_date as the leading column, for cross-offering date-range attendance
-- scans that the unique key (enrollment_id leading) cannot serve.
ALTER TABLE attendance
    ADD INDEX idx_attendance_class_date (class_date);
