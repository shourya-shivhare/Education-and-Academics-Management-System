-- =============================================================================
-- 06_last_seat_race.sql
-- Concurrency Demonstration: THE "LAST SEAT" OVERBOOKING RACE CONDITION
-- Database: edu_db | Tool: Open TWO separate MySQL Terminal / Workbench Sessions
--
-- DBMS Concept:
-- Demonstrates the classic concurrency anomaly in ticket/course reservations:
-- Two concurrent clients check available seats simultaneously, both observe
-- remaining capacity, and both execute an INSERT, leading to OVERBOOKING.
--
-- Part 1: The Naive Pattern (Fails - leads to overbooking or trigger collisions).
-- Part 2: The Safe Pattern using Pessimistic Locking (SELECT ... FOR UPDATE in sp_enroll_student).
-- =============================================================================

-- =============================================================================
-- EXPERIMENT SETUP: Create a dedicated 1-seat test offering
-- =============================================================================
INSERT INTO course_offering (offering_id, subject_id, faculty_id, term_id, section, capacity, status)
VALUES (9999, 1, 1, 5, 'Z', 1, 'Open')
ON DUPLICATE KEY UPDATE capacity = 1, status = 'Open';

-- Ensure no enrollments exist for Offering #9999
DELETE FROM enrollment WHERE offering_id = 9999;


-- =============================================================================
-- PART 1: The NAIVE Pattern (No Locking -> Race Condition)
-- Scenario: Both Session A and Session B check available seats using plain SELECT.
-- =============================================================================

-- [SESSION A - Step 1: Check capacity]
START TRANSACTION;
SELECT COUNT(*) AS current_enrolled FROM enrollment WHERE offering_id = 9999;
-- Returns 0. Capacity is 1. Session A concludes: "Seat is available!"

-- [SESSION B - Step 1: Concurrent check before A commits]
START TRANSACTION;
SELECT COUNT(*) AS current_enrolled FROM enrollment WHERE offering_id = 9999;
-- Also returns 0! Session B concludes: "Seat is available!"

-- [SESSION A - Step 2: Insert enrollment for Student 31]
INSERT INTO enrollment (student_id, offering_id, enroll_date, status)
VALUES (31, 9999, CURDATE(), 'Enrolled');
COMMIT;

-- [SESSION B - Step 2: Insert enrollment for Student 32]
-- Without locking or if done outside triggers, this would cause 2 students in a 1-seat class!
-- In our system, the BEFORE INSERT trigger detects the overbooking and rejects Session B:
INSERT INTO enrollment (student_id, offering_id, enroll_date, status)
VALUES (32, 9999, CURDATE(), 'Enrolled');
-- ERROR 1644 (45000): Enrollment rejected: Course offering has reached its maximum student capacity.
ROLLBACK;


-- =============================================================================
-- PART 2: The SAFE Pattern via Pessimistic Row Locking (sp_enroll_student)
-- Uses SELECT ... FOR UPDATE inside sp_enroll_student to serialize access.
-- =============================================================================
-- Reset offering #9999 to 0 enrolled
DELETE FROM enrollment WHERE offering_id = 9999;

-- [SESSION A - Calls sp_enroll_student for Student 31]
-- Inside the procedure:
-- 1. START TRANSACTION;
-- 2. SELECT ... FROM course_offering WHERE offering_id = 9999 FOR UPDATE; (Acquires X-Lock)
-- 3. Sleeps or processes insert:
START TRANSACTION;
SELECT offering_id, capacity, status 
FROM course_offering 
WHERE offering_id = 9999 
FOR UPDATE;

-- [SESSION B - Calls sp_enroll_student concurrently for Student 32]
-- Session B attempts:
-- SELECT ... FROM course_offering WHERE offering_id = 9999 FOR UPDATE;
-- RESULT: SESSION B BLOCKS! It cannot read or modify offering 9999 until Session A commits.

-- [SESSION A - Completes and commits]
INSERT INTO enrollment (student_id, offering_id, enroll_date, status)
VALUES (31, 9999, CURDATE(), 'Enrolled');
COMMIT;
-- (Session A releases the exclusive row lock)

-- [SESSION B - Instantly unblocks, acquires lock, evaluates current count]
-- Now Session B sees 1/1 seats occupied.
-- When it attempts to insert, it is rejected deterministically:
-- ERROR 1644 (45000): Enrollment rejected: Course offering has reached its maximum student capacity.

-- Cleanup test offering
DELETE FROM enrollment WHERE offering_id = 9999;
DELETE FROM course_offering WHERE offering_id = 9999;
