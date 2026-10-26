-- =============================================================================
-- V5__reference_data.sql - seed/reference data: departments, programs, terms,
-- grade_scale, classrooms. Runs as flyway_user on edu_db. IDs are deterministic
-- (auto_increment from 1) because this is the first insert into each table.
-- Department insert order -> department_id: 1=Computer Science, 2=Electronics &
-- Communication, 3=Mechanical Engineering, 4=Civil Engineering, 5=Mathematics,
-- 6=Physics.
-- =============================================================================

-- Departments (6). hod_faculty_id left NULL; assigned later by sp_assign_hod.
INSERT INTO department (name, established_year) VALUES
    ('Computer Science',             1998),
    ('Electronics & Communication',   1995),
    ('Mechanical Engineering',        1990),
    ('Civil Engineering',             1992),
    ('Mathematics',                   1985),
    ('Physics',                       1985);

-- Programs (a set) across the departments; department_id per the mapping above.
INSERT INTO program (name, degree, duration_years, department_id, total_credits) VALUES
    ('B.Sc. Computer Science',           'B.Sc.', 3, 1, 140),
    ('M.Sc. Computer Science',           'M.Sc.', 2, 1,  80),
    ('Ph.D. Computer Science',           'Ph.D.', 5, 1,  24),
    ('B.E. Electronics & Communication', 'B.E.',  4, 2, 160),
    ('B.E. Mechanical Engineering',      'B.E.',  4, 3, 160),
    ('B.E. Civil Engineering',            'B.E.',  4, 4, 160),
    ('M.Sc. Mathematics',                'M.Sc.', 2, 5,  80),
    ('M.Sc. Physics',                     'M.Sc.', 2, 6,  80);

-- Terms (6). Today is 2026-10-26, which falls in '2026-27 Odd', so that term is current.
INSERT INTO term (name, start_date, end_date, is_current) VALUES
    ('2024-25 Odd',  '2024-08-01', '2024-12-15', FALSE),
    ('2024-25 Even', '2025-01-06', '2025-05-15', FALSE),
    ('2025-26 Odd',  '2025-08-01', '2025-12-15', FALSE),
    ('2025-26 Even', '2026-01-06', '2026-05-15', FALSE),
    ('2026-27 Odd',  '2026-08-01', '2026-12-15', TRUE),
    ('2026-27 Even', '2027-01-06', '2027-05-15', FALSE);

-- Grade scale: O=10, A+=9, A=8, B+=7, B=6, C=5, P=4, F=0. 40% is the pass mark
-- (P, the lowest passing grade, starts at 40%; F is the only failing grade).
-- Ranges are contiguous and non-overlapping; the x.99 upper bounds make fractional
-- percentages resolve to exactly one grade.
INSERT INTO grade_scale (grade_letter, min_percent, max_percent, grade_point, is_pass) VALUES
    ('O',  90.00, 100.00, 10, TRUE),
    ('A+', 80.00,  89.99,  9, TRUE),
    ('A',  70.00,  79.99,  8, TRUE),
    ('B+', 60.00,  69.99,  7, TRUE),
    ('B',  50.00,  59.99,  6, TRUE),
    ('C',  45.00,  49.99,  5, TRUE),
    ('P',  40.00,  44.99,  4, TRUE),
    ('F',   0.00,  39.99,  0, FALSE);

-- Classrooms (a set) across campus buildings.
INSERT INTO classroom (building, room_no, capacity) VALUES
    ('A-Block',   '101',  60),
    ('A-Block',   '102',  60),
    ('A-Block',   '201',  45),
    ('B-Block',   '101', 120),
    ('B-Block',   '102', 120),
    ('C-Block',   '301',  30),
    ('C-Block',   '302',  30),
    ('Lab-Block', 'L1',   40);
