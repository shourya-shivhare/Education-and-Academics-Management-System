-- =============================================================================
-- 07_explain_index_proof.sql
-- Query Optimization & Index Proof: EXPLAIN with Index vs. IGNORE INDEX
-- Database: edu_db | Tool: MySQL Workbench / MySQL CLI / Terminal
--
-- DBMS Concept Demonstrated:
--   1. Query Optimizer Execution Plans (EXPLAIN / EXPLAIN ANALYZE).
--   2. B-Tree Clustered vs. Secondary Composite Indexes.
--   3. Index Utilization (`type: ref` / `range`) vs. Full Table Scan (`type: ALL`).
--   4. Leftmost Prefix Rule on Composite Indexes.
--   5. Full-Text Search indexing (`type: fulltext`) vs. wildcard string scans (`LIKE '%...'`).
--   6. IGNORE INDEX hint to force MySQL to bypass available indexes and prove
--      empirical performance differences (rows examined, execution time).
-- =============================================================================

USE edu_db;

-- =============================================================================
-- DEMO 1: Attendance Table (75,000+ Rows) - Composite Index Proof
-- Target Index: idx_attendance_offering_date ON attendance(offering_id, class_date)
-- Query: Retrieve attendance records for offering #1 within a specific date range.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Step 1A: Execution Plan WITH Composite Index
-- -----------------------------------------------------------------------------
EXPLAIN
SELECT 
    enrollment_id,
    status,
    COUNT(*) AS session_count
FROM attendance
WHERE offering_id = 1 
  AND class_date BETWEEN '2024-01-01' AND '2024-03-31'
GROUP BY enrollment_id, status;

/*
Expected Output WITH Index:
+----+-------------+------------+------------+-------+-----------------------------+-----------------------------+---------+------+------+----------+-----------------------------------------------------------+
| id | select_type | table      | partitions | type  | possible_keys               | key                         | key_len | ref  | rows | filtered | Extra                                                     |
+----+-------------+------------+------------+-------+-----------------------------+-----------------------------+---------+------+------+----------+-----------------------------------------------------------+
|  1 | SIMPLE      | attendance | NULL       | range | idx_attendance_offering_date| idx_attendance_offering_date| 7       | NULL |   45 |   100.00 | Using index condition; Using temporary; Using filesort    |
+----+-------------+------------+------------+-------+-----------------------------+-----------------------------+---------+------+------+----------+-----------------------------------------------------------+
Key Observations:
- type: 'range' (Index range scan using B-Tree index on (offering_id, class_date)).
- key: 'idx_attendance_offering_date' selected by the cost-based optimizer.
- rows examined: ~45 rows (out of ~75,540 total rows in the table!).
- selectivity: 99.94% of the table is skipped entirely without reading from disk.
*/

-- -----------------------------------------------------------------------------
-- Step 1B: Execution Plan WITHOUT Index (Forced Full Table Scan via IGNORE INDEX)
-- -----------------------------------------------------------------------------
EXPLAIN
SELECT 
    enrollment_id,
    status,
    COUNT(*) AS session_count
FROM attendance IGNORE INDEX (idx_attendance_offering_date, fk_attendance_offering)
WHERE offering_id = 1 
  AND class_date BETWEEN '2024-01-01' AND '2024-03-31'
GROUP BY enrollment_id, status;

/*
Expected Output WITHOUT Index (IGNORE INDEX):
+----+-------------+------------+------------+------+---------------+------+---------+------+-------+----------+----------------------------------------------+
| id | select_type | table      | partitions | type | possible_keys | key  | key_len | ref  | rows  | filtered | Extra                                        |
+----+-------------+------------+------------+------+---------------+------+---------+------+-------+----------+----------------------------------------------+
|  1 | SIMPLE      | attendance | NULL       | ALL  | NULL          | NULL | NULL    | NULL | 75540 |     1.11 | Using where; Using temporary; Using filesort |
+----+-------------+------------+------------+------+---------------+------+---------+------+-------+----------+----------------------------------------------+
Key Observations:
- type: 'ALL' (Full Table Scan - engine scans every single page of the table).
- key: NULL (No index can be used).
- rows examined: 75,540 rows read from disk/buffer pool.
- filtered: 1.11% (98.89% of evaluated rows are discarded post-read).
- I/O Cost: Substantially higher; triggers significant buffer pool thrashing on large datasets.
*/

-- -----------------------------------------------------------------------------
-- Step 1C: EXPLAIN ANALYZE (MySQL 8 Runtime Verification)
-- Measures actual execution time in milliseconds.
-- -----------------------------------------------------------------------------
-- With Index:
EXPLAIN ANALYZE
SELECT enrollment_id, status, COUNT(*) 
FROM attendance 
WHERE offering_id = 1 AND class_date BETWEEN '2024-01-01' AND '2024-03-31'
GROUP BY enrollment_id, status;
-- Expected: actual time: ~0.15ms .. 0.45ms, rows=45

-- Without Index:
EXPLAIN ANALYZE
SELECT enrollment_id, status, COUNT(*) 
FROM attendance IGNORE INDEX (idx_attendance_offering_date, fk_attendance_offering)
WHERE offering_id = 1 AND class_date BETWEEN '2024-01-01' AND '2024-03-31'
GROUP BY enrollment_id, status;
-- Expected: actual time: ~18.50ms .. 35.00ms, rows=75540 scanned (~80x slower!)


-- =============================================================================
-- DEMO 2: Fee Payment Lookup - Composite Index (student_id, term_id)
-- Target Index: idx_fee_payment_student_term ON fee_payment(student_id, term_id)
-- =============================================================================

-- Step 2A: Query using Composite Index
EXPLAIN
SELECT payment_id, amount, payment_date, payment_mode, status
FROM fee_payment
WHERE student_id = 42 AND term_id = 5;

/*
Expected Output WITH Index:
+----+-------------+-------------+------------+------+------------------------------+------------------------------+---------+-------------+------+----------+-------+
| id | select_type | table       | partitions | type | possible_keys                | key                          | key_len | ref         | rows | filtered | Extra |
+----+-------------+-------------+------------+------+------------------------------+------------------------------+---------+-------------+------+----------+-------+
|  1 | SIMPLE      | fee_payment | NULL       | ref  | idx_fee_payment_student_term | idx_fee_payment_student_term | 8       | const,const |    1 |   100.00 | NULL  |
+----+-------------+-------------+------------+------+------------------------------+------------------------------+---------+-------------+------+----------+-------+
Key Observations:
- type: 'ref' (Equality lookup on both parts of the composite key).
- ref: 'const,const' (Constants supplied for student_id and term_id).
- rows examined: exactly 1 row!
*/

-- Step 2B: Query forcing IGNORE INDEX
EXPLAIN
SELECT payment_id, amount, payment_date, payment_mode, status
FROM fee_payment IGNORE INDEX (idx_fee_payment_student_term, fk_fee_payment_student, fk_fee_payment_term)
WHERE student_id = 42 AND term_id = 5;

/*
Expected Output WITHOUT Index:
+----+-------------+-------------+------------+------+---------------+------+---------+------+------+----------+-------------+
| id | select_type | table       | partitions | type | possible_keys | key  | key_len | ref  | rows | filtered | Extra       |
+----+-------------+-------------+------------+------+---------------+------+---------+------+------+----------+-------------+
|  1 | SIMPLE      | fee_payment | NULL       | ALL  | NULL          | NULL | NULL    | NULL | 1800 |     0.55 | Using where |
+----+-------------+-------------+------------+------+---------------+------+---------+------+------+----------+-------------+
Key Observations:
- type: 'ALL' (Scans all 1,800 fee payment rows to find 1 record).
*/


-- =============================================================================
-- DEMO 3: Leftmost Prefix Rule on Composite Index
-- Target Index: idx_course_offering_subject_term ON course_offering(subject_id, term_id)
-- =============================================================================

-- Case 3A: Query with leading column (subject_id) -> INDEX USED
EXPLAIN
SELECT offering_id, section, capacity
FROM course_offering
WHERE subject_id = 10;

/*
Expected Output (Leading Column Present):
- type: 'ref'
- key: 'idx_course_offering_subject_term'
- key_len: 4 (Only the first 4 bytes for subject_id INT are used)
- rows: ~3-6
*/

-- Case 3B: Query with non-leading column ONLY (term_id) -> COMPOSITE INDEX CANNOT BE USED AS 'ref'
EXPLAIN
SELECT offering_id, section, capacity
FROM course_offering IGNORE INDEX (fk_offering_term)
WHERE term_id = 5;

/*
Expected Output (Leading Column Missing):
- type: 'ALL' or 'index' (Full index scan or full table scan)
- The B-Tree index is sorted first by subject_id, then by term_id.
  Without subject_id, the database cannot perform a logarithmic binary search
  directly to term_id, demonstrating the fundamental Leftmost Prefix Rule.
*/


-- =============================================================================
-- DEMO 4: FULLTEXT Index vs. Wildcard Pattern Scan
-- Target Index: ft_subject_search ON subject(code, title, syllabus)
-- =============================================================================

-- Step 4A: MATCH ... AGAINST with FULLTEXT Index
EXPLAIN
SELECT 
    subject_id, 
    code, 
    title,
    ROUND(MATCH(code, title, syllabus) AGAINST('Database Normalization SQL' IN NATURAL LANGUAGE MODE), 4) AS relevance_score
FROM subject
WHERE MATCH(code, title, syllabus) AGAINST('Database Normalization SQL' IN NATURAL LANGUAGE MODE);

/*
Expected Output (FULLTEXT Index):
+----+-------------+---------+------------+----------+-------------------+-------------------+---------+------+------+----------+-------------------------------+
| id | select_type | table   | partitions | type     | possible_keys     | key               | key_len | ref  | rows | filtered | Extra                         |
+----+-------------+---------+------------+----------+-------------------+-------------------+---------+------+------+----------+-------------------------------+
|  1 | SIMPLE      | subject | NULL       | fulltext | ft_subject_search | ft_subject_search | 0       | const|    1 |   100.00 | Using where; Ft_hints: sorted |
+----+-------------+---------+------------+----------+-------------------+-------------------+---------+------+------+----------+-------------------------------+
Key Observations:
- type: 'fulltext' (Uses the inverted index structure maintained by InnoDB).
- Rapid keyword lookups without scanning text blobs.
*/

-- Step 4B: Equivalent query using leading wildcard LIKE ('%term%')
EXPLAIN
SELECT subject_id, code, title
FROM subject
WHERE syllabus LIKE '%Database%' OR syllabus LIKE '%Normalization%' OR syllabus LIKE '%SQL%';

/*
Expected Output (Wildcard Scan):
+----+-------------+---------+------------+------+---------------+------+---------+------+------+----------+-------------+
| id | select_type | table   | partitions | type | possible_keys | key  | key_len | ref  | rows | filtered | Extra       |
+----+-------------+---------+------------+------+---------------+------+---------+------+------+----------+-------------+
|  1 | SIMPLE      | subject | NULL       | ALL  | NULL          | NULL | NULL    | NULL |   40 |    10.00 | Using where |
+----+-------------+---------+------------+------+---------------+------+---------+------+------+----------+-------------+
Key Observations:
- type: 'ALL' (Leading wildcards '%' prevent B-tree index usage, requiring string evaluation on every row).
*/
