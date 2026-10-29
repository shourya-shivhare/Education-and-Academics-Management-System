package edu.dbms.education.lab;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Three concurrency demonstrations:
 *
 * 1. last-seat-race   – two threads enroll into a 1-seat offering
 *                       naive mode: direct INSERT (race condition, may overbook)
 *                       safe mode:  CALL sp_enroll_student (SELECT…FOR UPDATE)
 *
 * 2. lost-update      – two threads overwrite the same marks row
 *                       naive mode:  simple UPDATE (second writer clobbers first)
 *                       optimistic:  UPDATE … WHERE version=? (conflict detected)
 *
 * 3. deadlock         – two threads lock two rows in reverse order
 *                       MySQL detects cycle, kills one transaction (error 1213)
 *                       the service catches 1213 and retries once (standard advice)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConcurrencyLabService {

    private final LabFixture                      fixture;
    private final @Qualifier("appDataSource") DataSource ds;

    // ══════════════════════════════════════════════════════════════════════
    // 1. Last-Seat Race Condition
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Both threads race to enroll into an offering that has capacity=1.
     *
     * Naive mode (safe=false):
     *   T1 and T2 both read capacity (no lock) → both see 1 available seat
     *   → both INSERT into enrollment.
     *   The UNIQUE(student_id, offering_id) constraint means the second INSERT
     *   will fail for the same student, but with two *different* students both
     *   INSERTs would succeed, producing enrollment_count > capacity.
     *   To simulate this faithfully, naive mode bypasses the sp_enroll_student
     *   procedure and does a raw INSERT.  The capacity constraint is only
     *   enforced in the trigger (trg_enrollment_before_insert), so there IS
     *   a narrow window.  We demonstrate the race by using a latch to force
     *   both threads to INSERT simultaneously after both have "checked" capacity.
     *
     * Safe mode (safe=true):
     *   Both threads call CALL sp_enroll_student(...).
     *   The procedure's SELECT…FOR UPDATE serialises them:
     *   T1 gets the lock, inserts, commits, releases.
     *   T2 then gets the lock, checks capacity/count, SIGNALs "already full".
     *   Result: exactly one enrollment; second thread gets SQLSTATE 45000.
     */
    public LabResult lastSeatRace(boolean safe) {
        var b = LabResult.builder("concurrency-last-seat-race");
        JdbcTemplate raw = new JdbcTemplate(ds);
        fixture.reset(raw);

        int sA         = fixture.studentA();
        int sB         = fixture.studentB();
        int offeringId = fixture.offeringSeat1();   // capacity=1

        b.step("Offering_id=" + offeringId + " capacity=1  (single seat)");
        b.step("Mode: " + (safe ? "SAFE – sp_enroll_student (SELECT…FOR UPDATE)"
                                 : "NAIVE – direct INSERT without locking"));
        b.step("Thread A enrolls student_id=" + sA +
               ",  Thread B enrolls student_id=" + sB);

        // Latch so both threads start their INSERT at the same moment
        CountDownLatch ready  = new CountDownLatch(2);
        CountDownLatch go     = new CountDownLatch(1);
        List<String>   result = new ArrayList<>();

        var executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        for (int[] pair : new int[][]{{sA, 0}, {sB, 1}}) {
            int sid  = pair[0];
            int slot = pair[1];
            executor.submit(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                try (Connection c = ds.getConnection()) {
                    c.setAutoCommit(false);
                    if (safe) {
                        enrollViaProcedure(c, sid, offeringId, b, slot);
                    } else {
                        enrollNaive(c, sid, offeringId, b, slot);
                    }
                } catch (Exception e) {
                    synchronized (result) {
                        result.add("T" + slot + " error: " + AcidLabService.rootMessage(e));
                    }
                    b.step("[T" + slot + "] OUTER ERROR: " + AcidLabService.rootMessage(e));
                }
            });
        }

        // Wait for both to be ready then release simultaneously
        try {
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            executor.shutdown();
            executor.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        // Measure actual enrollment count
        int enrolled = raw.queryForObject(
                "SELECT COUNT(*) FROM enrollment WHERE offering_id=? AND status='Enrolled'",
                Integer.class, offeringId);
        b.step("Final enrollment count for offering_id=" + offeringId + ": " + enrolled);

        if (safe) {
            b.outcome(enrolled == 1
                    ? "SAFE – sp_enroll_student serialised the race: exactly 1 seat filled"
                    : "UNEXPECTED at SAFE mode – enrolled=" + enrolled);
        } else {
            b.outcome(enrolled > 1
                    ? "RACE CONDITION – " + enrolled + " students enrolled into 1-seat offering (overbooking)"
                    : enrolled == 1
                    ? "RACE AVERTED by trigger (capacity check) – only 1 enrolled"
                    : "BOTH BLOCKED – check errors");
        }
        return b.build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // 2. Lost-Update on Marks
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Two threads read and overwrite the same marks row concurrently.
     *
     * Naive mode (optimistic=false):
     *   T1 reads marks_obtained=50, T2 reads marks_obtained=50.
     *   T1 writes 75, T2 writes 85.
     *   Final value is whichever committed last – T1's write is LOST.
     *   Lost update is observable because the committed value is 85 (T2 won)
     *   even though T1's update of 75 committed first.
     *
     * Optimistic mode (optimistic=true):
     *   Both read version=1.
     *   T1 commits: UPDATE … SET marks=75, version=version+1 WHERE version=1 → 1 row.
     *   T2 commits: UPDATE … SET marks=85, version=version+1 WHERE version=1 → 0 rows.
     *   T2 detects 0 rows → ConflictException → HTTP 409.
     *   Final value = 75 (T1's write preserved).
     */
    public LabResult lostUpdate(boolean optimistic) {
        var b = LabResult.builder("concurrency-lost-update");
        JdbcTemplate raw = new JdbcTemplate(ds);
        fixture.reset(raw);

        int sA         = fixture.studentA();
        int offeringId = fixture.offeringBig();

        // Enroll student A and create assessment + marks row
        raw.update("INSERT IGNORE INTO enrollment " +
                   "(student_id,offering_id,enroll_date,status,is_detained) " +
                   "VALUES (?,?,CURDATE(),'Enrolled',FALSE)", sA, offeringId);
        int enrollmentId = raw.queryForObject(
                "SELECT enrollment_id FROM enrollment WHERE student_id=? AND offering_id=?",
                Integer.class, sA, offeringId);

        raw.update("INSERT IGNORE INTO assessment " +
                   "(offering_id,assessment_no,title,max_marks,weightage) " +
                   "VALUES (?,1,'Midterm',100,50)", offeringId);
        raw.update("DELETE FROM marks WHERE enrollment_id=? AND offering_id=? AND assessment_no=1",
                   enrollmentId, offeringId);
        raw.update("INSERT INTO marks (enrollment_id,offering_id,assessment_no," +
                   "marks_obtained,version) VALUES (?,?,1,50.0,1)",
                   enrollmentId, offeringId);

        b.step("Setup: enrollment_id=" + enrollmentId + " initial marks=50 version=1");
        b.step("Mode: " + (optimistic ? "OPTIMISTIC LOCK (version check)"
                                       : "NAIVE (no version check)"));

        CountDownLatch bothRead  = new CountDownLatch(2);   // both threads have read the row
        CountDownLatch goWrite   = new CountDownLatch(1);   // release to write

        List<String> writeResults = new ArrayList<>();

        var executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        // T1: update marks to 75
        executor.submit(() -> runUpdate(enrollmentId, offeringId, 1,
                75.0, "T1", bothRead, goWrite, writeResults, b, optimistic));
        // T2: update marks to 85
        executor.submit(() -> runUpdate(enrollmentId, offeringId, 1,
                85.0, "T2", bothRead, goWrite, writeResults, b, optimistic));

        try {
            bothRead.await(5, TimeUnit.SECONDS);
            goWrite.countDown();
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        // Check final state
        Map<String, Object> finalRow = raw.queryForMap(
                "SELECT marks_obtained, version FROM marks " +
                "WHERE enrollment_id=? AND offering_id=? AND assessment_no=1",
                enrollmentId, offeringId);
        b.step("Final marks row: " + finalRow);
        b.step("Write results: " + writeResults);

        double finalMarks = ((Number) finalRow.get("marks_obtained")).doubleValue();
        if (optimistic) {
            boolean conflict = writeResults.stream().anyMatch(s -> s.contains("CONFLICT"));
            b.outcome(conflict
                    ? "OPTIMISTIC_LOCK – one write was rejected (0 rows). Final marks=" +
                      finalMarks + ". No lost update."
                    : "BOTH COMMITTED at optimistic mode (unexpected for version=1)");
        } else {
            b.outcome("NAIVE – last writer wins. Final marks=" + finalMarks +
                      ". One write was silently lost.");
        }
        return b.build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // 3. Deadlock
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Classic deadlock: two rows, two threads, opposite lock-acquisition order.
     *
     *   T1: LOCK row(assessment_no=1) → wait for row(assessment_no=2)
     *   T2: LOCK row(assessment_no=2) → wait for row(assessment_no=1)
     *
     * InnoDB detects the cycle (usually within 50 ms) and kills one transaction
     * with ER_LOCK_DEADLOCK (error 1213, SQLSTATE 40001).
     *
     * The service catches error 1213 and retries once – the standard MySQL
     * application-level deadlock-handling pattern.
     *
     * Both threads hold their first lock and WAIT for the second lock.
     * CountDownLatch ensures true simultaneity.
     */
    public LabResult deadlock() {
        var b = LabResult.builder("concurrency-deadlock");
        JdbcTemplate raw = new JdbcTemplate(ds);
        fixture.reset(raw);

        int sA         = fixture.studentA();
        int offeringId = fixture.offeringBig();

        // Enroll student A and create two assessment + marks rows
        raw.update("INSERT IGNORE INTO enrollment " +
                   "(student_id,offering_id,enroll_date,status,is_detained) " +
                   "VALUES (?,?,CURDATE(),'Enrolled',FALSE)", sA, offeringId);
        int enrollmentId = raw.queryForObject(
                "SELECT enrollment_id FROM enrollment WHERE student_id=? AND offering_id=?",
                Integer.class, sA, offeringId);

        raw.update("INSERT IGNORE INTO assessment " +
                   "(offering_id,assessment_no,title,max_marks,weightage) " +
                   "VALUES (?,1,'Midterm',100,50),(?,2,'Final',100,50)",
                   offeringId, offeringId);
        raw.update("DELETE FROM marks WHERE enrollment_id=? AND offering_id=? " +
                   "AND assessment_no IN (1,2)", enrollmentId, offeringId);
        raw.update("INSERT INTO marks (enrollment_id,offering_id,assessment_no," +
                   "marks_obtained,version) VALUES (?,?,1,50.0,1),(?,?,2,60.0,1)",
                   enrollmentId, offeringId, enrollmentId, offeringId);

        b.step("Setup: two marks rows (assessment_no=1 marks=50) and (assessment_no=2 marks=60)");
        b.step("T1 will lock row 1 first, then row 2");
        b.step("T2 will lock row 2 first, then row 1  → deadlock expected");

        // Latches to force true interleaving
        CountDownLatch t1LockedRow1 = new CountDownLatch(1);
        CountDownLatch t2LockedRow2 = new CountDownLatch(1);

        List<String> outcomes = new ArrayList<>();

        var executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        // T1: lock row1 → signal → wait for T2 to lock row2 → lock row2
        Future<?> f1 = executor.submit(() -> {
            int retries = 0;
            while (retries <= 1) {
                try (Connection c = ds.getConnection()) {
                    c.setAutoCommit(false);
                    b.step("[T1] START TRANSACTION");

                    // Lock row 1
                    updateMarksRow(c, enrollmentId, offeringId, 1, 75.0);
                    b.step("[T1] Locked marks row assessment_no=1, updated to 75");
                    t1LockedRow1.countDown();

                    // Wait for T2 to acquire its first lock
                    t2LockedRow2.await(3, TimeUnit.SECONDS);

                    // Now try to lock row 2 → deadlock candidate
                    b.step("[T1] Attempting to lock marks row assessment_no=2...");
                    updateMarksRow(c, enrollmentId, offeringId, 2, 75.0);
                    b.step("[T1] Locked row 2, marks updated to 75");

                    c.commit();
                    b.step("[T1] COMMIT");
                    synchronized (outcomes) { outcomes.add("T1: COMMITTED"); }
                    break;  // success
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1213) {
                        retries++;
                        b.step("[T1] DEADLOCK detected (ER_LOCK_DEADLOCK 1213). " +
                               (retries <= 1 ? "Retrying (attempt " + retries + ")..." : "Giving up."));
                        synchronized (outcomes) { outcomes.add("T1: DEADLOCK_retry=" + retries); }
                        try { Thread.sleep(50); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt(); break;
                        }
                    } else {
                        b.step("[T1] NON-DEADLOCK ERROR: " + e.getMessage());
                        synchronized (outcomes) { outcomes.add("T1: error=" + e.getMessage()); }
                        break;
                    }
                } catch (Exception e) {
                    b.step("[T1] ERROR: " + e.getMessage());
                    synchronized (outcomes) { outcomes.add("T1: error=" + e.getMessage()); }
                    t1LockedRow1.countDown();
                    break;
                }
            }
        });

        // T2: lock row2 → signal → wait for T1 to lock row1 → lock row1
        Future<?> f2 = executor.submit(() -> {
            // Small delay so T1 gets its first lock first
            try { Thread.sleep(30); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); return;
            }
            int retries = 0;
            while (retries <= 1) {
                try (Connection c = ds.getConnection()) {
                    c.setAutoCommit(false);
                    b.step("[T2] START TRANSACTION");

                    // Lock row 2 first
                    updateMarksRow(c, enrollmentId, offeringId, 2, 85.0);
                    b.step("[T2] Locked marks row assessment_no=2, updated to 85");
                    t2LockedRow2.countDown();

                    // Wait for T1 to have locked row 1
                    t1LockedRow1.await(3, TimeUnit.SECONDS);

                    // Now try to lock row 1 → forms the deadlock cycle
                    b.step("[T2] Attempting to lock marks row assessment_no=1...");
                    updateMarksRow(c, enrollmentId, offeringId, 1, 85.0);
                    b.step("[T2] Locked row 1, marks updated to 85");

                    c.commit();
                    b.step("[T2] COMMIT");
                    synchronized (outcomes) { outcomes.add("T2: COMMITTED"); }
                    break;
                } catch (SQLException e) {
                    if (e.getErrorCode() == 1213) {
                        retries++;
                        b.step("[T2] DEADLOCK detected (ER_LOCK_DEADLOCK 1213). " +
                               (retries <= 1 ? "Retrying..." : "Giving up."));
                        synchronized (outcomes) { outcomes.add("T2: DEADLOCK_retry=" + retries); }
                        try { Thread.sleep(80); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt(); break;
                        }
                    } else {
                        b.step("[T2] NON-DEADLOCK ERROR: " + e.getMessage());
                        synchronized (outcomes) { outcomes.add("T2: error=" + e.getMessage()); }
                        break;
                    }
                } catch (Exception e) {
                    b.step("[T2] ERROR: " + e.getMessage());
                    synchronized (outcomes) { outcomes.add("T2: error=" + e.getMessage()); }
                    t2LockedRow2.countDown();
                    break;
                }
            }
        });

        try {
            f1.get(20, TimeUnit.SECONDS);
            f2.get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            b.step("Thread wait timeout: " + e.getMessage());
        }
        executor.shutdownNow();

        // Show final marks state
        List<Map<String, Object>> finalRows = raw.queryForList(
                "SELECT assessment_no, marks_obtained, version FROM marks " +
                "WHERE enrollment_id=? AND offering_id=? AND assessment_no IN (1,2)",
                enrollmentId, offeringId);
        b.step("Final marks state: " + finalRows);
        b.step("Thread outcomes: " + outcomes);
        b.step("InnoDB deadlock notes: run 'SHOW ENGINE INNODB STATUS' to see last deadlock info.");

        boolean deadlockDetected = outcomes.stream().anyMatch(s -> s.contains("DEADLOCK"));
        boolean retried          = outcomes.stream().anyMatch(s -> s.contains("retry"));
        b.outcome(deadlockDetected
                ? "DEADLOCK detected (error 1213) and " + (retried ? "retried once" : "logged") +
                  ". One transaction survived."
                : "NO DEADLOCK – both transactions serialised without cycle.");
        return b.build();
    }

    // ── private helpers ───────────────────────────────────────────────────

    private void enrollViaProcedure(Connection c, int studentId, int offeringId,
                                    LabResult.Builder b, int slot) throws SQLException {
        String callSql = "CALL sp_enroll_student(?, ?)";
        try (PreparedStatement ps = c.prepareStatement(callSql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, offeringId);
            ps.execute();
        }
        c.commit();
        b.step("[T" + slot + "] sp_enroll_student(" + studentId + "," + offeringId +
               ") → COMMITTED");
    }

    private void enrollNaive(Connection c, int studentId, int offeringId,
                             LabResult.Builder b, int slot) throws SQLException {
        // Naive: read capacity (no lock), then insert
        int cap;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT capacity FROM course_offering WHERE offering_id=?")) {
            ps.setInt(1, offeringId);
            try (ResultSet rs = ps.executeQuery()) {
                cap = rs.next() ? rs.getInt(1) : 0;
            }
        }
        b.step("[T" + slot + "] Read capacity=" + cap + " (no lock)");

        // Simulate think-time to maximise race window
        try { Thread.sleep(20); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

        String insertSql =
                "INSERT INTO enrollment (student_id,offering_id,enroll_date,status,is_detained)" +
                " VALUES (?,?,CURDATE(),'Enrolled',FALSE)";
        try (PreparedStatement ps = c.prepareStatement(insertSql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, offeringId);
            ps.execute();
        }
        c.commit();
        b.step("[T" + slot + "] INSERT enrollment for student_id=" + studentId + " → COMMITTED");
    }

    private void updateMarksRow(Connection c, int enrollmentId, int offeringId,
                                int assessmentNo, double newMarks) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE marks SET marks_obtained=? " +
                "WHERE enrollment_id=? AND offering_id=? AND assessment_no=?")) {
            ps.setDouble(1, newMarks);
            ps.setInt(2, enrollmentId);
            ps.setInt(3, offeringId);
            ps.setInt(4, assessmentNo);
            ps.executeUpdate();
        }
    }

    private void runUpdate(int enrollmentId, int offeringId, int assessmentNo,
                           double newMarks, String label,
                           CountDownLatch bothRead, CountDownLatch goWrite,
                           List<String> results, LabResult.Builder b,
                           boolean optimistic) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);

            // Read current version
            int version;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT marks_obtained, version FROM marks " +
                    "WHERE enrollment_id=? AND offering_id=? AND assessment_no=?")) {
                ps.setInt(1, enrollmentId);
                ps.setInt(2, offeringId);
                ps.setInt(3, assessmentNo);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    version = rs.getInt("version");
                    b.step("[" + label + "] Read marks_obtained=" + rs.getDouble("marks_obtained") +
                           " version=" + version);
                }
            }

            bothRead.countDown();
            goWrite.await(5, TimeUnit.SECONDS);

            // Write
            int rows;
            if (optimistic) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE marks SET marks_obtained=?, version=version+1 " +
                        "WHERE enrollment_id=? AND offering_id=? AND assessment_no=? " +
                        "AND version=?")) {
                    ps.setDouble(1, newMarks);
                    ps.setInt(2, enrollmentId);
                    ps.setInt(3, offeringId);
                    ps.setInt(4, assessmentNo);
                    ps.setInt(5, version);
                    rows = ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE marks SET marks_obtained=? " +
                        "WHERE enrollment_id=? AND offering_id=? AND assessment_no=?")) {
                    ps.setDouble(1, newMarks);
                    ps.setInt(2, enrollmentId);
                    ps.setInt(3, offeringId);
                    ps.setInt(4, assessmentNo);
                    rows = ps.executeUpdate();
                }
            }

            if (rows == 0 && optimistic) {
                c.rollback();
                b.step("[" + label + "] UPDATE returned 0 rows (version mismatch) → ROLLBACK");
                synchronized (results) { results.add(label + ": CONFLICT – 0 rows updated"); }
            } else {
                c.commit();
                b.step("[" + label + "] UPDATE rows=" + rows + " marks→" + newMarks + " → COMMIT");
                synchronized (results) { results.add(label + ": COMMITTED marks=" + newMarks); }
            }
        } catch (Exception e) {
            b.step("[" + label + "] ERROR: " + e.getMessage());
            synchronized (results) { results.add(label + ": error=" + e.getMessage()); }
        }
    }
}
