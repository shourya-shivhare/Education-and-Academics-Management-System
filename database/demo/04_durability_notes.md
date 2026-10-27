# ACID Durability in MySQL 8 (InnoDB Engine)

## 1. What is Durability?
**Durability** guarantees that once a transaction commits (`COMMIT`), its state changes are permanently recorded in non-volatile storage and will survive any subsequent system failure, power outage, operating system crash, or database engine crash.

---

## 2. InnoDB Architectural Mechanisms for Durability

```
+--------------------------------------------------------------------------+
|                        MySQL Server / InnoDB Engine                      |
|                                                                          |
|   +--------------------------+         +-----------------------------+   |
|   |   InnoDB Buffer Pool     |         |    Redo Log Buffer          |   |
|   |  (In-memory dirty pages) |         | (In-memory transaction log) |   |
|   +------------+-------------+         +--------------+--------------+   |
|                |                                      |                  |
+----------------|--------------------------------------|------------------+
                 | (Background asynchronous flush)      | (Synchronous at COMMIT)
                 v                                      v
+-------------------------------+        +---------------------------------+
|      Doublewrite Buffer       |        |        InnoDB Redo Logs         |
| (Guards against torn pages)   |        |  (#ib_redo* / Write-Ahead Log)  |
+----------------+--------------+        +---------------------------------+
                 |                                      |
                 v                                      |
+-----------------------------------+                   |
|     InnoDB Tablespace (.ibd)      |<------------------+
|   (Persistent clustered index)    |  Crash Recovery (Redo Roll-Forward)
+-----------------------------------+
```

### A. The Write-Ahead Logging (WAL) Protocol
Writing 16KB modified database pages (dirty pages) directly to random disk sectors at every commit would create severe I/O bottlenecks. InnoDB resolves this using **Write-Ahead Logging (WAL)**:
1. When a transaction modifies a row, the changes are recorded in memory in the **Buffer Pool** and simultaneously written sequentially to the **Redo Log Buffer**.
2. At `COMMIT`, only the lightweight sequential **Redo Log** is flushed and synced to physical storage (`fsync()`).
3. The dirty data pages in the Buffer Pool are lazily written to `.ibd` tablespace files by background flusher threads. If the system crashes before dirty pages reach the disk, InnoDB reads the Redo Log during restart to re-apply (roll-forward) all committed modifications.

---

### B. The `innodb_flush_log_at_trx_commit` Parameter
This system variable controls the strictness of the durability guarantee:

| Value | Behavior at `COMMIT` | Durability Level | Performance | Risk |
|:---:|:---|:---:|:---:|:---|
| **`1`** *(Default)* | The Redo Log buffer is written to the OS page cache AND flushed to physical disk via `fsync()` at **every** transaction commit. | **100% Strict ACID Compliant** | Baseline | **Zero data loss** on power failure or OS crash. |
| **`0`** | The Redo Log buffer is written to disk and `fsync()` executed only once per second. Commits do nothing to disk. | Weak | Highest (~5–10x faster) | Up to **1 second of committed transactions lost** if MySQL crashes. |
| **`2`** | The Redo Log buffer is written to the operating system file cache at every commit, but flushed to disk via `fsync()` only once per second. | Moderate | High | Survives MySQL server crashes; loses up to **1 second of transactions** on full OS or power failure. |

> **College Coursework Rule:** In financial and academic record systems, `innodb_flush_log_at_trx_commit = 1` must remain active to prevent lost tuition payments or grade records.

---

### C. The Doublewrite Buffer (Torn Page Prevention)
Disk writes occur in 4KB operating system blocks, while InnoDB pages are 16KB. If a sudden power loss occurs midway through writing a 16KB page, a **torn page (partial page write)** results, corrupting the page header and data checksums.

InnoDB prevents this via the **Doublewrite Buffer**:
- Pages are first written contiguously to the Doublewrite storage area on disk.
- Only after the contiguous write is verified does InnoDB flush the page to its actual tablespace position.
- If a crash occurs during tablespace flushing, InnoDB restores the intact original page from the Doublewrite Buffer and applies Redo Logs on top.

---

## 3. Crash Recovery Lifecycle (ARIES Protocol)

When MySQL starts up after an abrupt shutdown or crash:
1. **Analysis Pass:** Scans the Redo Log forward from the last checkpoint to identify active transactions that were uncommitted at crash time and dirty pages in memory.
2. **Redo Phase (Roll-Forward):** Applies all committed redo log entries to tablespaces, bringing physical disk pages up to the exact state at the moment of the crash.
3. **Undo Phase (Roll-Back):** Uses the Undo Tablespace (`undo_001`, `undo_002`) to roll back transactions that were active (uncommitted) when the crash occurred, preserving **Atomicity** and **Consistency**.

---

## 4. Manual Verification Exercise

Run these commands in MySQL to observe Durability in action:

```sql
-- 1. Check current durability setting (must be 1 for strict ACID)
SHOW VARIABLES LIKE 'innodb_flush_log_at_trx_commit';

-- 2. Insert an academic record inside a transaction and commit
START TRANSACTION;
INSERT INTO fee_payment (student_id, term_id, amount, payment_date, payment_mode, status)
VALUES (31, 5, 88888.00, CURDATE(), 'Online', 'Paid');
COMMIT;

-- 3. In terminal / admin console, forcibly terminate and restart MySQL:
-- Windows: net stop MySQL80 && net start MySQL80
-- Linux:   sudo systemctl restart mysql

-- 4. Re-query the database immediately upon reconnection:
SELECT * FROM fee_payment WHERE amount = 88888.00;
-- The committed row survives intact, proven by Redo Log recovery!
```
