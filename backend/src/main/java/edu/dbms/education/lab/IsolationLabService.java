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

/**
 * Isolation level demonstration.
 *
 * Supported levels (request param): READ_UNCOMMITTED, READ_COMMITTED,
 *                                   REPEATABLE_READ (default), SERIALIZABLE.
 *
 * Scenario (Dirty-Read / Non-Repeatable-Read / Phantom-Read test):
 *
 * Two threads operate on the SAME lab offering capacity column.
 *
 *   Writer thread (T1):
 *     SET SESSION TRANSACTION ISOLATION LEVEL ...
 *     START TRANSACTION
 *     UPDATE course_offering SET capacity = 99 WHERE offering_id = ?
 *     <<< pause 200 ms to let reader observe >>>
 *     ROLLBACK  (never commits)
 *
 *   Reader thread (T2):
 *     SET SESSION TRANSACTION ISOLATION LEVEL ...
 *     START TRANSACTION
 *     <<< read #1 before writer updates >>>
 *     <<< wait for writer to update (uncommitted) >>>
 *     <<< read #2 after writer's uncommitted UPDATE >>>
 *     <<< wait for writer to ROLLBACK >>>
 *     <<< read #3 after rollback >>>
 *     COMMIT
 *
 * Observations by isolation level:
 *   READ_UNCOMMITTED : read #2 sees capacity=99 (DIRTY READ) ← anomaly
 *   READ_COMMITTED   : read #2 sees original  (no dirty read)
 *   REPEATABLE_READ  : read #2 and #3 see original (snapshot from tx start)
 *   SERIALIZABLE     : T2 blocks until T1 finishes (no concurrent reads)
 *
 * Because this involves two connections with explicit transaction control,
 * we manage JDBC connections directly rather than using @Transactional.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IsolationLabService {

    private final LabFixture                      fixture;
    private final @Qualifier("appDataSource") DataSource ds;

    public LabResult isolation(String levelName) {
        var b = LabResult.builder("acid-isolation");
        JdbcTemplate raw = new JdbcTemplate(ds);
        fixture.reset(raw);

        String mysqlLevel = toMysqlLevel(levelName);
        int offeringId    = fixture.offeringSeat1();  // capacity=1

        // Reset to a known capacity so reads are predictable
        raw.update("UPDATE course_offering SET capacity=5 WHERE offering_id=?", offeringId);
        int originalCapacity = raw.queryForObject(
                "SELECT capacity FROM course_offering WHERE offering_id=?",
                Integer.class, offeringId);
        b.step("Isolation level requested: " + levelName + " (MySQL: " + mysqlLevel + ")");
        b.step("Setup: offering_id=" + offeringId + " original capacity=" + originalCapacity);

        // Synchronisation points between T1 (writer) and T2 (reader)
        CountDownLatch writerUpdated   = new CountDownLatch(1);  // T1 has done the UPDATE
        CountDownLatch readerRead2Done = new CountDownLatch(1);  // T2 finished read #2
        CountDownLatch writerDone      = new CountDownLatch(1);  // T1 has rolled back

        List<Integer> reads = new ArrayList<>();   // 0=read1, 1=read2, 2=read3
        List<String>  errors = new ArrayList<>();

        var executor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        // ── T2: Reader ────────────────────────────────────────────────────
        Future<?> readerFuture = executor.submit(() -> {
            try (Connection c = ds.getConnection()) {
                c.setAutoCommit(false);
                setIsolationLevel(c, mysqlLevel);
                b.step("[T2-Reader] Connection opened, isolation=" + mysqlLevel);

                // Read #1: baseline before writer changes anything
                reads.add(readCapacity(c, offeringId));
                b.step("[T2-Reader] Read #1 (before writer updates): capacity=" + reads.get(0));

                // Wait for writer to commit/update but NOT yet rollback
                writerUpdated.await();
                reads.add(readCapacity(c, offeringId));
                b.step("[T2-Reader] Read #2 (after writer's uncommitted UPDATE): capacity=" + reads.get(1) +
                       (reads.get(1) == 99 ? " ← DIRTY READ (anomaly)" :
                        reads.get(1).equals(reads.get(0)) ? " ← clean (no dirty read)" : ""));
                readerRead2Done.countDown();

                // Wait for writer to rollback
                writerDone.await();
                reads.add(readCapacity(c, offeringId));
                b.step("[T2-Reader] Read #3 (after writer ROLLBACK): capacity=" + reads.get(2) +
                       (reads.get(2) == 99 ? " ← phantom data persists (unexpected)" :
                        " ← rollback visible, no phantoms"));

                c.commit();
                b.step("[T2-Reader] COMMIT");
            } catch (Exception e) {
                errors.add("T2: " + e.getMessage());
                b.step("[T2-Reader] ERROR: " + e.getMessage());
                readerRead2Done.countDown();
            }
        });

        // ── T1: Writer ────────────────────────────────────────────────────
        Future<?> writerFuture = executor.submit(() -> {
            try (Connection c = ds.getConnection()) {
                c.setAutoCommit(false);
                setIsolationLevel(c, mysqlLevel);
                b.step("[T1-Writer] Connection opened, isolation=" + mysqlLevel);

                // Dirty UPDATE (will be rolled back)
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE course_offering SET capacity=99 WHERE offering_id=?")) {
                    ps.setInt(1, offeringId);
                    int rows = ps.executeUpdate();
                    b.trace("UPDATE course_offering SET capacity=99 WHERE offering_id=?",
                            List.of(String.valueOf(offeringId)), rows, 0);
                    b.step("[T1-Writer] UPDATE capacity→99 (uncommitted). Rows=" + rows);
                }

                writerUpdated.countDown();       // signal reader to proceed
                readerRead2Done.await();         // wait for reader's read #2

                // Roll back – this UPDATE never commits
                c.rollback();
                b.step("[T1-Writer] ROLLBACK – UPDATE undone");
                writerDone.countDown();
            } catch (Exception e) {
                errors.add("T1: " + e.getMessage());
                b.step("[T1-Writer] ERROR: " + e.getMessage());
                writerDone.countDown();
                readerRead2Done.countDown();
            }
        });

        // Wait for both threads (max 10 s)
        try {
            readerFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
            writerFuture.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            b.step("Timeout waiting for threads: " + e.getMessage());
        }
        executor.shutdownNow();

        // Analyse result
        b.step("Summary: reads=" + reads + " level=" + mysqlLevel);
        if (!errors.isEmpty()) {
            b.outcome("ERROR – " + String.join("; ", errors));
        } else if (reads.size() >= 2) {
            boolean dirtyRead = reads.get(1) == 99;
            String outcome;
            if ("READ UNCOMMITTED".equals(mysqlLevel)) {
                outcome = dirtyRead ? "DIRTY_READ observed (READ UNCOMMITTED anomaly)"
                                    : "NO dirty read at READ UNCOMMITTED (unexpected)";
            } else {
                outcome = dirtyRead ? "DIRTY_READ observed at " + mysqlLevel + " (unexpected)"
                                    : "NO dirty read at " + mysqlLevel + " (isolation held)";
            }
            b.outcome(outcome);
        } else {
            b.outcome("INCONCLUSIVE – fewer than expected reads recorded");
        }
        return b.build();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private int readCapacity(Connection c, int offeringId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT capacity FROM course_offering WHERE offering_id=?")) {
            ps.setInt(1, offeringId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private void setIsolationLevel(Connection c, String level) throws SQLException {
        try (var stmt = c.createStatement()) {
            stmt.execute("SET SESSION TRANSACTION ISOLATION LEVEL " + level);
        }
    }

    private static String toMysqlLevel(String name) {
        if (name == null) return "REPEATABLE READ";
        return switch (name.toUpperCase().replace("_", " ")) {
            case "READ UNCOMMITTED", "READ_UNCOMMITTED" -> "READ UNCOMMITTED";
            case "READ COMMITTED",   "READ_COMMITTED"   -> "READ COMMITTED";
            case "SERIALIZABLE"                         -> "SERIALIZABLE";
            default                                     -> "REPEATABLE READ";
        };
    }
}
