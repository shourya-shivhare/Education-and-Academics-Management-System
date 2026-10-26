-- =============================================================================
-- V3__alter_circular_fk.sql - resolve the circular Department <-> Faculty HOD FK
-- and demonstrate one MODIFY COLUMN.
-- department.hod_faculty_id could not be created in V1 because it references faculty,
-- while faculty.department_id (V1) references department - a creation-order cycle.
-- Both tables exist by now, so the column + FK can be attached safely.
-- =============================================================================

-- 1) Add the nullable HOD column to department.
ALTER TABLE department
    ADD COLUMN hod_faculty_id INT NULL;

-- 2) Attach the circular FK. RESTRICT: a faculty who is currently an HOD cannot be
--    deleted until the department's hod_faculty_id is cleared/reassigned (by a
--    stored procedure, e.g. sp_assign_hod).
ALTER TABLE department
    ADD CONSTRAINT fk_department_hod
        FOREIGN KEY (hod_faculty_id) REFERENCES faculty(faculty_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

-- 3) MODIFY COLUMN example: widen subject.title to allow longer course titles.
ALTER TABLE subject
    MODIFY COLUMN title VARCHAR(200) NOT NULL;
