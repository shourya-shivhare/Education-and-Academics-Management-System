-- =============================================================================
-- 05_deadlock.sql
-- Concurrency Demonstration: DEADLOCK DETECTION AND VICTIM ROLLBACK (Error 1213)
-- Database: edu_db | Tool: Open TWO separate MySQL Terminal / Workbench Sessions
--
-- DBMS Concept:
-- A Deadlock occurs when two or more transactions have a circular dependency:
-- each transaction holds a lock that the other needs, and neither can proceed.
--
-- InnoDB's Deadlock Detector:
-- InnoDB maintains a directed 'Wait-For Graph' (WFG). When a cycle is detected,
-- InnoDB automatically breaks the deadlock by choosing a 'victim' transaction
-- (the one with the smallest undo log / fewest modifications) and rolling it back,
-- returning:
--   ERROR 1213 (40001): Deadlock found when trying to get lock; try restarting transaction
-- The surviving transaction acquires the requested lock and proceeds to completion.
-- =============================================================================

-- Setup: Ensure two distinct marks rows exist for testing
SELECT enrollment_id, offering_id, assessment_no, marks_obtained 
FROM marks 
WHERE enrollment_id = 1 AND offering_id = 1 AND assessment_no IN (1, 2);


-- =============================================================================
-- INTERACTIVE TWO-SESSION WALKTHROUGH
-- Follow steps strictly in numbered chronological order (T1 -> T2 -> T3 -> T4):
-- =============================================================================

-- [TIME T1 - SESSION A]
-- Session A starts transaction and acquires an exclusive lock on Row 1 (assessment #1)
START TRANSACTION;
UPDATE marks 
SET marks_obtained = 45.00 
WHERE enrollment_id = 1 AND offering_id = 1 AND assessment_no = 1;
-- Output A: Query OK, 1 row affected. (Holds Exclusive Lock on Row 1)


-- [TIME T2 - SESSION B]
-- Session B starts transaction and acquires an exclusive lock on Row 2 (assessment #2)
START TRANSACTION;
UPDATE marks 
SET marks_obtained = 48.00 
WHERE enrollment_id = 1 AND offering_id = 1 AND assessment_no = 2;
-- Output B: Query OK, 1 row affected. (Holds Exclusive Lock on Row 2)


-- [TIME T3 - SESSION A]
-- Session A now requests an exclusive lock on Row 2 (currently held by Session B):
UPDATE marks 
SET marks_obtained = 46.00 
WHERE enrollment_id = 1 AND offering_id = 1 AND assessment_no = 2;
-- Result: SESSION A BLOCKS (waits for Session B to release Row 2)


-- [TIME T4 - SESSION B (Deadlock Trigger)]
-- Session B now requests an exclusive lock on Row 1 (currently held by Session A):
-- This creates the circular wait dependency: A waits for B, and B waits for A!
UPDATE marks 
SET marks_obtained = 42.00 
WHERE enrollment_id = 1 AND offering_id = 1 AND assessment_no = 1;

-- =============================================================================
-- OBSERVED RESULTS:
-- =============================================================================
-- 1. In SESSION B:
--    InnoDB immediately detects the cycle and rolls back Session B:
--    ERROR 1213 (40001): Deadlock found when trying to get lock; try restarting transaction
--
-- 2. In SESSION A:
--    Because Session B was rolled back, Session B's lock on Row 2 was released.
--    Session A instantly UNBLOCKS and successfully updates Row 2!
--    Session A can now COMMIT cleanly:
COMMIT;

-- Session B must acknowledge the rollback:
ROLLBACK;


-- =============================================================================
-- POST-DEADLOCK ENGINE DIAGNOSTICS
-- Inspect the latest deadlock details in the InnoDB engine status:
-- =============================================================================
SHOW ENGINE INNODB STATUS\G
-- Search output for section: '------------------------ LATEST DETECTED DEADLOCK ------------------------'
-- Shows the exact SQL statements, thread IDs, and lock types involved in the cycle.
