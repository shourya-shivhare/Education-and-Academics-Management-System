# Database Demonstration & Educational Scripts

This directory contains the 10 hands-on demonstration scripts and educational notes for the **Education and Academics Management System**. These scripts demonstrate core relational database engine behaviors, transaction internals, concurrency anomalies, query optimization, role-based security, and architectural defenses.

---

## Directory Index

| Script / Document | Core DBMS Concept | Description |
| :--- | :--- | :--- |
| [`01_acid_atomicity.sql`](file:///d:/College%20academics/SEM-5/dbms/project/Education-and-Academics-Management-System/database/demo/01_acid_atomicity.sql) | **Atomicity ("All or Nothing")** | Multi-operation transaction (Fee Payment + Enrollment), forced failure triggering full `ROLLBACK`, and partial rollback using `SAVEPOINT` / `ROLLBACK TO SAVEPOINT`. |
| [`02_acid_consistency.sql`](file:///d:/College%20academics/SEM-5/dbms/project/Education-and-Academics-Management-System/database/demo/02_acid_consistency.sql) | **Consistency & Constraints** | Defense of relational invariants: `CHECK` constraint violations, orphan `FOREIGN KEY` rejection, duplicate `UNIQUE` roll numbers, and business rules enforced via triggers (`SIGNAL SQLSTATE '45000'`). |
| [`03_isolation_levels.sql`](file:///d:/College%20academics/SEM-5/dbms/project/Education-and-Academics-Management-System/database/demo/03_isolation_levels.sql) | **Isolation & Concurrency** | Step-by-step two-session execution instructions across all 4 SQL-92 isolation levels (`READ UNCOMMITTED`, `READ COMMITTED`, `REPEATABLE READ`, `SERIALIZABLE`), demonstrating dirty reads, non-repeatable reads, and MySQL InnoDB Next-Key locking against phantoms. |
| [`04_durability_notes.md`](file:///d:/College%20academics/SEM-5/dbms/project/Education-and-Academics-Management-System/database/demo/04_durability_notes.md) | **Durability & Crash Recovery** | Deep-dive documentation on InnoDB Write-Ahead Logging (WAL), Redo Log (`ib_logfile`), Doublewrite Buffer, and ACID tradeoffs with `innodb_flush_log_at_trx_commit` (0, 1, 2) and `sync_binlog`. |
| [`05_deadlock.sql`](file:///d:/College%20academics/SEM-5/dbms/project/Education-and-Academics-Management-System/database/demo/05_deadlock.sql) | **Deadlock Detection** | Two-session opposite-order row updates producing cyclic wait-for graph, triggering InnoDB deadlock detection, transaction rollback (`ERROR 1213`), and `SHOW ENGINE INNODB STATUS` analysis. |
| [`06_last_seat_race.sql`](file:///d:/College%20academics/SEM-5/dbms/project/Education-and-Academics-Management-System/database/demo/06_last_seat_race.sql) | **Race Conditions & Locking** | Concurrency race condition on the last available seat in a course offering: Naive check-then-act leading to overbooking vs. pessimistic locking with `SELECT ... FOR UPDATE` inside `sp_enroll_student`. |
| [`07_explain_index_proof.sql`](file:///d:/College%20academics/SEM-5/dbms/project/Education-and-Academics-Management-System/database/demo/07_explain_index_proof.sql) | **Query Optimizer & Indexing** | Empirical execution plan analysis using `EXPLAIN` and `EXPLAIN ANALYZE`: Composite B-Tree index scan (`type: range/ref`, ~45 rows) vs. forced full table scan with `IGNORE INDEX` (`type: ALL`, 75,540 rows examined), leftmost prefix rule, and full-text inverted index searches. |
| [`08_rbac_demo.sql`](file:///d:/College%20academics/SEM-5/dbms/project/Education-and-Academics-Management-System/database/demo/08_rbac_demo.sql) | **Role-Based Access Control** | Creates concrete MySQL users (`demo_registrar`, `demo_faculty`, `demo_student`, `demo_advisor`), tests least-privilege roles, shows unauthorized operations blocked with `ERROR 1142`, tests column-level grants (`ERROR 1143`), and demonstrates dynamic `REVOKE`. |
| [`09_ddl_alter_drop_truncate.sql`](file:///d:/College%20academics/SEM-5/dbms/project/Education-and-Academics-Management-System/database/demo/09_ddl_alter_drop_truncate.sql) | **Data Definition Language (DDL)** | Sandbox schema evolution: `ALTER TABLE` (ADD/MODIFY/DROP column, CHECK constraints), `RENAME TABLE`, `TRUNCATE TABLE` vs. `DELETE` (page deallocation & auto-increment reset), and foreign key cascade protection against destructive `DROP TABLE`. |
| [`10_sql_injection_demo.md`](file:///d:/College%20academics/SEM-5/dbms/project/Education-and-Academics-Management-System/database/demo/10_sql_injection_demo.md) | **Application Security & SQLi** | The single designated educational SQL injection document required by Rule 3: demonstrates `' OR '1'='1` authentication bypass and `UNION SELECT` data leakage against vulnerable string concatenation vs. binary-protocol parameterized queries (`?`). |

---

## How to Run Demo Scripts

All `.sql` scripts in this folder can be executed against MySQL 8 (InnoDB) using MySQL Workbench, DBeaver, or the MySQL Command-Line Client:

```bash
# Connect as administrative root user to run RBAC or DDL demos:
mysql -u root -p edu_db < database/demo/01_acid_atomicity.sql
mysql -u root -p edu_db < database/demo/07_explain_index_proof.sql
mysql -u root -p edu_db < database/demo/08_rbac_demo.sql
```

For two-session concurrency scripts (`03_isolation_levels.sql`, `05_deadlock.sql`, `06_last_seat_race.sql`), open two separate terminal windows or Workbench query tabs as instructed in each file's header comments.
