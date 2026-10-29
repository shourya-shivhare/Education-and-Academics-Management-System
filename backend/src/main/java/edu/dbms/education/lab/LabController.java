package edu.dbms.education.lab;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * /api/labs – ACID and concurrency demonstrations.  ADMIN only.
 *
 * All endpoints return a LabResult (NOT the standard ResponseEnvelope)
 * because multi-threaded labs build their own SQL trace independently of
 * the request-scoped SqlTraceService.
 *
 * ┌──────────────────────────────────────────────────┬──────────────────────────────────┐
 * │ Endpoint                                         │ Demonstrates                     │
 * ├──────────────────────────────────────────────────┼──────────────────────────────────┤
 * │ POST /api/labs/acid/atomicity                    │ ACID – Atomicity                 │
 * │      ?forceFailure=true                          │   forced rollback mid-transaction│
 * │ POST /api/labs/acid/consistency                  │ ACID – Consistency               │
 * │ POST /api/labs/acid/isolation                    │ ACID – Isolation levels          │
 * │      ?level=READ_UNCOMMITTED | READ_COMMITTED    │                                  │
 * │            | REPEATABLE_READ | SERIALIZABLE      │                                  │
 * │ POST /api/labs/acid/durability                   │ ACID – Durability (WAL)          │
 * │ GET  /api/labs/acid/durability/verify            │   post-restart proof query       │
 * ├──────────────────────────────────────────────────┼──────────────────────────────────┤
 * │ POST /api/labs/concurrency/last-seat-race        │ Race – last-seat overbooking     │
 * │      ?safe=false (default)                       │   naive: direct INSERT           │
 * │      ?safe=true                                  │   safe:  sp_enroll_student       │
 * │ POST /api/labs/concurrency/lost-update           │ Lost update on marks             │
 * │      ?optimistic=false (default)                 │   naive overwrite                │
 * │      ?optimistic=true                            │   version-check detection        │
 * │ POST /api/labs/concurrency/deadlock              │ Deadlock + retry pattern         │
 * └──────────────────────────────────────────────────┴──────────────────────────────────┘
 */
@RestController
@RequestMapping("/api/labs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LabController {

    private final AcidLabService        acidLab;
    private final IsolationLabService   isolationLab;
    private final ConcurrencyLabService concurrencyLab;
    private final LabFixture            fixture;

    // ══════════════════════════════════════════════════════════════════════
    // ACID
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Atomicity: enroll + fee-payment in one transaction.
     * forceFailure=true injects a duplicate INSERT to force rollback.
     * The final SELECT proves the rollback (both tables empty for the lab student).
     */
    @PostMapping("/acid/atomicity")
    public ResponseEntity<LabResult> atomicity(
            @RequestParam(defaultValue = "false") boolean forceFailure) {
        return ResponseEntity.ok(acidLab.atomicity(forceFailure));
    }

    /**
     * Consistency: three deliberate constraint violations, each in its own
     * transaction.  Returns the exact DB error for each violation.
     */
    @PostMapping("/acid/consistency")
    public ResponseEntity<LabResult> consistency() {
        return ResponseEntity.ok(acidLab.consistency());
    }

    /**
     * Isolation: two-thread scenario showing dirty reads, non-repeatable reads,
     * and phantom reads depending on the chosen level.
     *
     * level: READ_UNCOMMITTED | READ_COMMITTED | REPEATABLE_READ | SERIALIZABLE
     *        default = REPEATABLE_READ
     */
    @PostMapping("/acid/isolation")
    public ResponseEntity<LabResult> isolation(
            @RequestParam(defaultValue = "REPEATABLE_READ") String level) {
        return ResponseEntity.ok(isolationLab.isolation(level));
    }

    /**
     * Durability: commits a fee-payment row and returns the InnoDB WAL explanation.
     * Verify after restart with GET /api/labs/acid/durability/verify.
     */
    @PostMapping("/acid/durability")
    public ResponseEntity<LabResult> durability() {
        return ResponseEntity.ok(acidLab.durability());
    }

    /**
     * Post-restart durability proof: queries for the committed row.
     * Expected to return the row inserted by POST /api/labs/acid/durability
     * even after a MySQL or JVM restart.
     */
    @GetMapping("/acid/durability/verify")
    public ResponseEntity<LabResult> durabilityVerify() {
        var b   = LabResult.builder("acid-durability-verify");
        LabJdbc lab = new LabJdbc(fixture.getDs(), b);

        int sA = fixture.studentA();
        b.step("Querying fee_payment for lab student_id=" + sA +
               " (should survive restart due to InnoDB durability)");
        var rows = lab.query(
                "SELECT payment_id, amount, payment_date, status " +
                "FROM fee_payment WHERE student_id=? ORDER BY payment_id DESC", sA);
        b.step("Found " + rows.size() + " row(s): " + rows);
        b.outcome(rows.isEmpty()
                ? "NO_ROW – run POST /api/labs/acid/durability first"
                : "DURABLE – " + rows.size() + " payment row(s) survived (WAL confirmed)");
        return ResponseEntity.ok(b.build());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Concurrency
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Last-seat race condition.
     * safe=false → naive INSERT without locking  (may overbook)
     * safe=true  → sp_enroll_student with SELECT…FOR UPDATE (exactly 1 enrolls)
     */
    @PostMapping("/concurrency/last-seat-race")
    public ResponseEntity<LabResult> lastSeatRace(
            @RequestParam(defaultValue = "false") boolean safe) {
        return ResponseEntity.ok(concurrencyLab.lastSeatRace(safe));
    }

    /**
     * Lost-update on marks.
     * optimistic=false → naive UPDATE (second write clobbers first)
     * optimistic=true  → UPDATE…WHERE version=? (conflict detected, 0 rows)
     */
    @PostMapping("/concurrency/lost-update")
    public ResponseEntity<LabResult> lostUpdate(
            @RequestParam(defaultValue = "false") boolean optimistic) {
        return ResponseEntity.ok(concurrencyLab.lostUpdate(optimistic));
    }

    /**
     * Deadlock: two threads update two rows in reverse order.
     * InnoDB detects the cycle (error 1213) and the service retries once.
     */
    @PostMapping("/concurrency/deadlock")
    public ResponseEntity<LabResult> deadlock() {
        return ResponseEntity.ok(concurrencyLab.deadlock());
    }
}
