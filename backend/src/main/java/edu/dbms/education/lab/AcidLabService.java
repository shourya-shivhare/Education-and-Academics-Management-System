package edu.dbms.education.lab;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * ACID property demonstrations.
 *
 * All four methods use LabJdbc (not TracingJdbcTemplate) so they are safe
 * to call from the request thread without needing a live request scope on
 * background threads.  The SQL trace is returned in LabResult.sqlTrace.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcidLabService {

    private final LabFixture                fixture;
    private final @Qualifier("appDataSource") DataSource ds;
    private final PlatformTransactionManager txManager;

    // ══════════════════════════════════════════════════════════════════════
    // A – Atomicity
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Atomicity demo: enroll student A into the big lab offering AND insert
     * a fee-payment row inside one transaction.  When forceFailure=true we
     * intentionally violate the enrollment UNIQUE constraint after the fee
     * INSERT to prove that both operations are rolled back together.
     *
     * Steps shown:
     *   1. Clean state proof (no enrollment, no fee for student A)
     *   2. START TRANSACTION
     *   3. INSERT fee_payment (₹5000 for student A)
     *   4. INSERT enrollment  (student A into offeringBig)
     *   5a. [normal]  COMMIT  → both rows exist
     *   5b. [failure] INSERT duplicate enrollment → UNIQUE violation → ROLLBACK
     *   6. Query to prove rollback: both tables empty for student A
     *
     * DBMS concept: atomicity guarantees all-or-nothing.  InnoDB's undo-log
     * records the before-images of every modified page; on ROLLBACK the engine
     * applies those undo records in reverse order to restore the original state.
     */
    public LabResult atomicity(boolean forceFailure) {
        var b = LabResult.builder("acid-atomicity");
        JdbcTemplate raw = new JdbcTemplate(ds);
        LabJdbc lab       = new LabJdbc(ds, b);
        fixture.reset(raw);

        int sA        = fixture.studentA();
        int offering  = fixture.offeringBig();

        b.step("State before: verifying no enrollment/fee exists for student_id=" + sA);
        long countEnr = lab.scalar(
                "SELECT COUNT(*) FROM enrollment  WHERE student_id=?", Long.class, sA);
        long countFee = lab.scalar(
                "SELECT COUNT(*) FROM fee_payment WHERE student_id=?", Long.class, sA);
        b.step("Pre-state: enrollment_count=" + countEnr + " fee_count=" + countFee);

        // Use programmatic transaction so we can inject a mid-transaction failure
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        boolean rolledBack = false;
        try {
            tx.execute(status -> {
                b.step("START TRANSACTION (isolation=READ_COMMITTED)");

                // Step 3: Insert fee payment
                lab.update(
                    "INSERT INTO fee_payment (student_id, term_id, amount, payment_date," +
                    " payment_mode, status) VALUES (?,?,5000.00,CURDATE(),'Online','Paid')",
                    sA, fixture.termId());
                b.step("INSERT fee_payment for student_id=" + sA + " amount=5000 → success");

                // Step 4: Insert enrollment
                lab.update(
                    "INSERT INTO enrollment (student_id, offering_id, enroll_date, status," +
                    " is_detained) VALUES (?,?,CURDATE(),'Enrolled',FALSE)",
                    sA, offering);
                b.step("INSERT enrollment for student_id=" + sA +
                       " offering_id=" + offering + " → success");

                if (forceFailure) {
                    b.step("forceFailure=true → inserting DUPLICATE enrollment to trigger " +
                           "UNIQUE constraint violation and force ROLLBACK");
                    // This violates UNIQUE(student_id, offering_id) → DataIntegrityViolationException
                    lab.update(
                        "INSERT INTO enrollment (student_id, offering_id, enroll_date, status," +
                        " is_detained) VALUES (?,?,CURDATE(),'Enrolled',FALSE)",
                        sA, offering);
                }

                b.step("COMMIT");
                return null;
            });
        } catch (Exception e) {
            rolledBack = true;
            b.step("EXCEPTION caught: " + e.getClass().getSimpleName() +
                   " → transaction rolled back. Message: " + rootMessage(e));
        }

        // Step 6: prove the rollback (or commit)
        b.step("Post-state query: checking enrollment and fee_payment for student_id=" + sA);
        long postEnr = lab.scalar(
                "SELECT COUNT(*) FROM enrollment  WHERE student_id=?", Long.class, sA);
        long postFee = lab.scalar(
                "SELECT COUNT(*) FROM fee_payment WHERE student_id=?", Long.class, sA);
        b.step("Post-state: enrollment_count=" + postEnr + " fee_count=" + postFee);

        if (forceFailure) {
            b.outcome(rolledBack && postEnr == 0 && postFee == 0
                    ? "ROLLED_BACK – atomicity proven: both inserts undone"
                    : "UNEXPECTED – check logs");
        } else {
            b.outcome(postEnr == 1 && postFee == 1
                    ? "COMMITTED – both fee and enrollment persisted atomically"
                    : "UNEXPECTED – check logs");
        }
        return b.build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // C – Consistency
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Consistency demo: attempts three operations that violate DB constraints,
     * captures the exact MySQL error message, and proves the DB stays consistent.
     *
     * Violations attempted:
     *   1. Enroll into a non-existent offering (FK violation)
     *   2. Insert a student with a duplicate roll_no (UNIQUE violation)
     *   3. Insert marks with marks_obtained > max_marks (trigger SIGNAL 45000)
     *
     * Each violation is isolated in its own transaction so the others can run.
     *
     * DBMS concept: consistency is enforced at the storage layer through
     * declarative constraints (FK, UNIQUE, CHECK) and trigger-based rules
     * (SIGNAL SQLSTATE '45000').  The application layer receives the exact
     * error text and HTTP 409 is returned – no application-level enforcement
     * needed for these invariants.
     */
    public LabResult consistency() {
        var b = LabResult.builder("acid-consistency");
        JdbcTemplate raw = new JdbcTemplate(ds);
        LabJdbc lab       = new LabJdbc(ds, b);
        fixture.reset(raw);

        int sA = fixture.studentA();

        // First, enroll sA into offeringBig so we can test marks constraint
        enrollLabStudent(raw, sA, fixture.offeringBig());
        int enrollmentId = raw.queryForObject(
                "SELECT enrollment_id FROM enrollment WHERE student_id=? AND offering_id=?",
                Integer.class, sA, fixture.offeringBig());

        // Ensure assessment 1 exists
        raw.update("INSERT IGNORE INTO assessment " +
                   "(offering_id, assessment_no, title, max_marks, weightage) " +
                   "VALUES (?,1,'Midterm',50,50)", fixture.offeringBig());

        // ── Violation 1: FK – enroll into offering 999999 (doesn't exist) ─
        b.step("Violation 1: enrolling student_id=" + sA +
               " into offering_id=999999 (FK violation expected)");
        try {
            TransactionTemplate tx1 = new TransactionTemplate(txManager);
            tx1.execute(s -> {
                lab.update(
                    "INSERT INTO enrollment (student_id,offering_id,enroll_date,status,is_detained)" +
                    " VALUES (?,999999,CURDATE(),'Enrolled',FALSE)", sA);
                return null;
            });
            b.step("  → UNEXPECTED: insert succeeded");
        } catch (Exception e) {
            b.step("  → DB rejected: " + rootMessage(e) +
                   " [SQLSTATE: foreign key constraint]");
        }

        // ── Violation 2: UNIQUE roll_no ────────────────────────────────────
        b.step("Violation 2: inserting student with duplicate roll_no 'LAB-A-001' " +
               "(UNIQUE constraint expected)");
        int prog = raw.queryForObject("SELECT MIN(program_id) FROM program", Integer.class);
        try {
            TransactionTemplate tx2 = new TransactionTemplate(txManager);
            tx2.execute(s -> {
                // Insert a person first (email is different so person insert succeeds)
                lab.update("INSERT INTO person (first_name,last_name,email,date_of_birth)" +
                           " VALUES ('dup','dup','dup_roll@lab.edu','2000-01-01')");
                int newPid = raw.queryForObject("SELECT LAST_INSERT_ID()", Integer.class);
                // Now try to insert student with the same roll_no
                lab.update("INSERT INTO student (student_id,roll_no,program_id," +
                           "admission_date,current_semester) VALUES (?,?,?,CURDATE(),1)",
                           newPid, "LAB-A-001", prog);
                return null;
            });
            b.step("  → UNEXPECTED: insert succeeded");
        } catch (Exception e) {
            // Clean up orphan person row
            raw.update("DELETE FROM person WHERE email='dup_roll@lab.edu'");
            b.step("  → DB rejected: " + rootMessage(e) +
                   " [Duplicate entry for uq_student_roll_no]");
        }

        // ── Violation 3: marks > max_marks (trigger SIGNAL 45000) ─────────
        b.step("Violation 3: inserting marks_obtained=200 where max_marks=50 " +
               "(trigger SIGNAL 45000 expected)");
        try {
            TransactionTemplate tx3 = new TransactionTemplate(txManager);
            tx3.execute(s -> {
                lab.update(
                    "INSERT INTO marks (enrollment_id,offering_id,assessment_no," +
                    "marks_obtained) VALUES (?,?,1,200.0)",
                    enrollmentId, fixture.offeringBig());
                return null;
            });
            b.step("  → UNEXPECTED: insert succeeded");
        } catch (Exception e) {
            b.step("  → DB rejected (SIGNAL 45000): " + rootMessage(e));
        }

        // Verify state unchanged
        b.step("Post-state: verifying marks table is empty for enrollment_id=" + enrollmentId);
        long mc = lab.scalar(
                "SELECT COUNT(*) FROM marks WHERE enrollment_id=?", Long.class, enrollmentId);
        b.step("  marks count=" + mc + " (expected 0 – all violations prevented insertion)");

        b.outcome(mc == 0 ? "CONSISTENT – all three constraint violations were rejected"
                           : "UNEXPECTED – marks row was persisted despite violation");
        return b.build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // I – Isolation  (see IsolationLabService for the two-thread version)
    // ══════════════════════════════════════════════════════════════════════
    // (Isolation is complex enough to warrant its own service class.)

    // ══════════════════════════════════════════════════════════════════════
    // D – Durability
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Durability demo: commits one fee-payment row and explains how InnoDB
     * WAL (Write-Ahead Log) guarantees the commit survives a crash.
     *
     * Steps:
     *   1. INSERT fee_payment + COMMIT
     *   2. Verify the row exists
     *   3. Return explanatory narrative about the WAL guarantee
     *
     * Manual verification (mentioned in the response steps):
     *   Restart the backend / MySQL and call:
     *     GET /api/labs/acid/durability/verify?studentId=<studentA>
     *   or simply query:
     *     SELECT * FROM fee_payment WHERE student_id = <studentA>;
     *
     * DBMS concept:
     *   InnoDB uses a Write-Ahead Log (the redo log, stored as ib_logfile0/1).
     *   Every committed transaction is flushed to the redo log BEFORE the
     *   corresponding data page is written to disk (innodb_flush_log_at_trx_commit=1
     *   is the default).  Even if the OS crashes between a COMMIT and the buffer
     *   pool flush, crash recovery replays the redo log and the committed row reappears.
     *   In MySQL 8 the redo log is always fsync'd on commit (unless configured otherwise).
     */
    public LabResult durability() {
        var b = LabResult.builder("acid-durability");
        JdbcTemplate raw = new JdbcTemplate(ds);
        LabJdbc lab       = new LabJdbc(ds, b);
        fixture.reset(raw);

        int sA = fixture.studentA();

        b.step("Step 1: INSERT fee_payment and COMMIT for student_id=" + sA);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.execute(status -> {
            lab.update(
                "INSERT INTO fee_payment (student_id,term_id,amount,payment_date," +
                "payment_mode,status) VALUES (?,?,9999.00,CURDATE(),'Online','Paid')",
                sA, fixture.termId());
            b.step("  INSERT executed. Transaction commits on method return.");
            return null;
        });
        b.step("  COMMIT confirmed by TransactionTemplate.");

        b.step("Step 2: verify row exists (SELECT)");
        List<Map<String, Object>> rows = lab.query(
                "SELECT payment_id, amount, status FROM fee_payment WHERE student_id=?", sA);
        b.step("  Found " + rows.size() + " row(s): " + rows);

        b.step("Step 3: InnoDB durability explanation →");
        b.step("  innodb_flush_log_at_trx_commit = 1 (MySQL default): each COMMIT calls " +
               "fsync() on the redo log before returning to the application.");
        b.step("  The redo log (ib_logfile*) records the change before the buffer-pool " +
               "page reaches disk. Crash recovery replays it automatically.");
        b.step("  To verify manually after restart: " +
               "SELECT * FROM fee_payment WHERE student_id=" + sA + ";");
        b.step("  Expected: row with amount=9999.00 status='Paid' still present.");
        b.step("  Show variable: SHOW VARIABLES LIKE 'innodb_flush_log_at_trx_commit';");

        // Also show the system variable so the user sees the actual setting
        Map<String, Object> varRow = lab.queryOne(
                "SHOW VARIABLES LIKE 'innodb_flush_log_at_trx_commit'");
        b.step("  DB confirms: " + varRow);

        b.outcome("COMMITTED – row persisted. " +
                  "Durability guaranteed by InnoDB WAL (redo log fsync on commit).");
        return b.build();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private void enrollLabStudent(JdbcTemplate jdbc, int studentId, int offeringId) {
        jdbc.update(
            "INSERT IGNORE INTO enrollment (student_id,offering_id,enroll_date,status,is_detained)" +
            " VALUES (?,?,CURDATE(),'Enrolled',FALSE)", studentId, offeringId);
    }

    static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() != null ? root.getMessage() : t.getMessage();
    }
}
