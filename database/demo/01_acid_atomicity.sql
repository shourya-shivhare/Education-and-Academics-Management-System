-- =============================================================================
-- 01_acid_atomicity.sql
-- ACID Property Demonstration: ATOMICITY ("All or Nothing")
-- Database: edu_db | Tool: MySQL Workbench / MySQL CLI
--
-- DBMS Concept:
-- A transaction is an indivisible unit of work. Either all operations succeed
-- and are committed permanently, or if an error occurs at any point, all
-- intermediate modifications are undone (rolled back), leaving the database in
-- its original state prior to the transaction.
-- =============================================================================

-- =============================================================================
-- PART 1: Successful Multi-Operation Atomic Transaction
-- Scenario: Student #31 makes a fee payment of INR 35,000 and registers for Offering #1.
-- =============================================================================
START TRANSACTION;

-- Step 1: Insert fee payment record
INSERT INTO fee_payment (student_id, term_id, amount, payment_date, payment_mode, status)
VALUES (31, 5, 35000.00, CURDATE(), 'Online', 'Paid');

-- Step 2: Record course enrollment
INSERT INTO enrollment (student_id, offering_id, enroll_date, status)
VALUES (31, 1, CURDATE(), 'Enrolled');

-- Step 3: Commit both actions atomically
COMMIT;

-- Verification: Both records exist together
SELECT 'Step 1: Fee Payment Verified' AS check_point, payment_id, student_id, amount, status 
FROM fee_payment WHERE student_id = 31 AND amount = 35000.00;

SELECT 'Step 2: Enrollment Verified' AS check_point, enrollment_id, student_id, offering_id, status 
FROM enrollment WHERE student_id = 31 AND offering_id = 1;


-- =============================================================================
-- PART 2: Forced Failure on Step 2 & Proof of Complete Rollback
-- Scenario: A fee payment is inserted, but the subsequent enrollment attempt
--           violates a check constraint (invalid negative credits / bad offering).
-- =============================================================================
START TRANSACTION;

-- Step 1: Insert fee payment record (amount = 99999.00 as distinctive marker)
INSERT INTO fee_payment (student_id, term_id, amount, payment_date, payment_mode, status)
VALUES (31, 5, 99999.00, CURDATE(), 'Card', 'Paid');

-- Step 2: Forced Failure - attempt to insert an enrollment with a non-existent offering ID (-999)
-- Violates Foreign Key constraint fk_enrollment_offering:
-- ERROR 1452 (23000): Cannot add or update a child row: a foreign key constraint fails
INSERT INTO enrollment (student_id, offering_id, enroll_date, status)
VALUES (31, -999, CURDATE(), 'Enrolled');

-- If an error occurs, the transaction is rolled back:
ROLLBACK;

-- Verification of Atomicity:
-- The fee payment of 99999.00 was ROLLED BACK and DOES NOT EXIST in the table.
SELECT 'Verification: Fee Payment Rolled Back' AS result, COUNT(*) AS matching_rows
FROM fee_payment 
WHERE amount = 99999.00;
-- Expected Output: matching_rows = 0


-- =============================================================================
-- PART 3: Partial Rollback Using SAVEPOINT and ROLLBACK TO SAVEPOINT
-- Scenario: Student makes a tuition payment, sets a SAVEPOINT, attempts an optional
--           hostel fee payment that fails, rolls back ONLY to the savepoint, and commits.
-- =============================================================================
START TRANSACTION;

-- Step 1: Base tuition payment
INSERT INTO fee_payment (student_id, term_id, amount, payment_date, payment_mode, status)
VALUES (31, 5, 42000.00, CURDATE(), 'Online', 'Paid');

-- Establish intermediate checkpoint
SAVEPOINT tuition_paid;

-- Step 2: Attempt secondary payment that will be rejected or cancelled
INSERT INTO fee_payment (student_id, term_id, amount, payment_date, payment_mode, status)
VALUES (31, 5, 12000.00, CURDATE(), 'Cash', 'Pending');

-- Partial rollback: undo ONLY Step 2, retaining Step 1
ROLLBACK TO SAVEPOINT tuition_paid;

-- Commit remaining uncancelled operations
COMMIT;

-- Verification of Savepoint:
-- 42,000.00 tuition payment is persisted; 12,000.00 cash payment was undone.
SELECT payment_id, student_id, amount, status 
FROM fee_payment 
WHERE student_id = 31 AND amount IN (42000.00, 12000.00);
-- Expected Output: Exactly 1 row (42,000.00)
