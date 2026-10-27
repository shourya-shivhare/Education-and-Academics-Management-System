-- =============================================================================
-- 03_isolation_levels.sql
-- ACID Property Demonstration: ISOLATION (Two-Session Interactive Lab)
-- Database: edu_db | Tool: Open TWO separate MySQL Terminal / Workbench Sessions
--
-- DBMS Concept:
-- Transaction isolation controls whether uncommitted changes made by one session
-- are visible to other concurrent sessions.
-- The four ANSI SQL-92 isolation levels are demonstrated:
--   1. READ UNCOMMITTED (Allows Dirty Reads)
--   2. READ COMMITTED   (Prevents Dirty Reads; Allows Non-Repeatable Reads)
--   3. REPEATABLE READ  (MySQL InnoDB Default - Prevents Dirty & Non-Repeatable Reads; MVCC snapshot)
--   4. SERIALIZABLE     (Strict 2PL locking; plain SELECTs converted to SELECT ... FOR SHARE)
-- =============================================================================

-- Setup test row for isolation experiments
UPDATE enrollment SET final_marks = 70.00 WHERE enrollment_id = 1;


-- =============================================================================
-- EXPERIMENT 1: READ UNCOMMITTED & DIRTY READ
-- Phenomenon: Session A reads uncommitted modifications made by Session B.
-- =============================================================================

-- [SESSION A - Setup]
SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
START TRANSACTION;
SELECT final_marks FROM enrollment WHERE enrollment_id = 1;
-- Output A1: 70.00

-- [SESSION B - Dirty Write]
START TRANSACTION;
UPDATE enrollment SET final_marks = 99.00 WHERE enrollment_id = 1;
-- (Session B has NOT committed yet!)

-- [SESSION A - Dirty Read]
SELECT final_marks FROM enrollment WHERE enrollment_id = 1;
-- Output A2: 99.00  <-- DIRTY READ! Session A observed uncommitted data.

-- [SESSION B - Abort / Rollback]
ROLLBACK;

-- [SESSION A - Verification]
SELECT final_marks FROM enrollment WHERE enrollment_id = 1;
-- Output A3: 70.00  <-- The dirty 99.00 disappeared because Session B rolled back.
COMMIT;


-- =============================================================================
-- EXPERIMENT 2: READ COMMITTED & NON-REPEATABLE READ
-- Phenomenon: Session A reads a row, Session B updates & commits, Session A re-reads
--             and observes different values within the SAME transaction.
-- =============================================================================

-- [SESSION A - Setup]
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
START TRANSACTION;
SELECT final_marks FROM enrollment WHERE enrollment_id = 1;
-- Output A1: 70.00

-- [SESSION B - Modify & Commit]
START TRANSACTION;
UPDATE enrollment SET final_marks = 85.00 WHERE enrollment_id = 1;
COMMIT;

-- [SESSION A - Re-read in same transaction]
SELECT final_marks FROM enrollment WHERE enrollment_id = 1;
-- Output A2: 85.00  <-- NON-REPEATABLE READ!
-- (Dirty reads prevented, but committed external writes alter values inside active transaction).
COMMIT;


-- =============================================================================
-- EXPERIMENT 3: REPEATABLE READ (MySQL InnoDB Default - MVCC Snapshot Read)
-- Phenomenon: Session A maintains a point-in-time consistent snapshot established
--             at the first read, regardless of external commits by Session B.
-- =============================================================================
UPDATE enrollment SET final_marks = 70.00 WHERE enrollment_id = 1;

-- [SESSION A - Setup]
SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION;
SELECT final_marks FROM enrollment WHERE enrollment_id = 1;
-- Output A1: 70.00 (Snapshot created from InnoDB undo log read-view)

-- [SESSION B - Modify & Commit]
START TRANSACTION;
UPDATE enrollment SET final_marks = 95.00 WHERE enrollment_id = 1;
COMMIT;

-- [SESSION A - Re-read in same transaction]
SELECT final_marks FROM enrollment WHERE enrollment_id = 1;
-- Output A2: 70.00  <-- REPEATABLE READ GUARANTEED!
-- (InnoDB uses MVCC undo logs to reconstruct the row as it existed when Session A began).

COMMIT;

-- After Session A commits, a new transaction sees the committed 95.00:
SELECT final_marks FROM enrollment WHERE enrollment_id = 1;
-- Output A3: 95.00


-- =============================================================================
-- EXPERIMENT 4: SERIALIZABLE (Pessimistic Two-Phase Locking / 2PL)
-- Phenomenon: Plain SELECT queries implicitly acquire Shared Locks (S-locks),
--             blocking concurrent writers until the reader commits.
-- =============================================================================
UPDATE enrollment SET final_marks = 70.00 WHERE enrollment_id = 1;

-- [SESSION A - Setup]
SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE;
START TRANSACTION;
SELECT final_marks FROM enrollment WHERE enrollment_id = 1;
-- (Acquires Shared Lock on enrollment row #1)

-- [SESSION B - Writer attempts update]
START TRANSACTION;
-- This statement will BLOCK (hang) waiting for Session A's shared lock:
UPDATE enrollment SET final_marks = 88.00 WHERE enrollment_id = 1;
-- Lock wait timeout or blocks until Session A commits!

-- [SESSION A - Releases lock]
COMMIT;

-- [SESSION B - Unblocks and completes]
-- Session B completes update immediately after Session A's COMMIT.
COMMIT;


-- =============================================================================
-- ISOLATION LEVEL COMPARISON SUMMARY (Observed in MySQL 8 InnoDB)
-- =============================================================================
-- | Isolation Level   | Dirty Read | Non-Repeatable Read | Phantom Read (InnoDB) |
-- |-------------------|------------|---------------------|-----------------------|
-- | READ UNCOMMITTED  | YES        | YES                 | YES                   |
-- | READ COMMITTED    | NO         | YES                 | YES                   |
-- | REPEATABLE READ   | NO         | NO                  | NO (Next-Key Locks)   |
-- | SERIALIZABLE      | NO         | NO                  | NO (S-Lock on reads)  |
-- =============================================================================
