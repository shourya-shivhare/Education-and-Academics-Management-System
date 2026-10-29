package edu.dbms.education.lab;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Return value for every lab endpoint.
 *
 * Shape:
 * {
 *   "scenario":  "acid-atomicity",
 *   "outcome":   "ROLLED_BACK | COMMITTED | DETECTED | ...",
 *   "steps":     [ "Step 1: ...", "Step 2: ..." ],
 *   "sqlTrace":  [ { "sql": "...", "params": [...], "rowsAffected": N, "timeMs": M } ]
 * }
 *
 * Unlike normal API responses, LabResult is NOT wrapped in ResponseEnvelope
 * so the lab can accumulate its own trace independently of the request-scoped
 * SqlTraceService (multi-threaded labs run SQL on background threads where
 * the request scope is not active).
 */
@Getter
public class LabResult {

    private final String          scenario;
    private final String          outcome;
    private final List<String>    steps;
    private final List<LabTrace>  sqlTrace;

    private LabResult(Builder b) {
        this.scenario = b.scenario;
        this.outcome  = b.outcome;
        this.steps    = Collections.unmodifiableList(b.steps);
        this.sqlTrace = Collections.unmodifiableList(b.sqlTrace);
    }

    // ── Immutable trace entry ──────────────────────────────────────────────

    @Getter
    public static class LabTrace {
        private final String       sql;
        private final List<String> params;
        private final int          rowsAffected;
        private final long         timeMs;
        private final String       thread;   // thread name – useful for race/deadlock demos

        public LabTrace(String sql, List<String> params, int rowsAffected,
                        long timeMs, String thread) {
            this.sql          = sql;
            this.params       = params == null ? List.of() : List.copyOf(params);
            this.rowsAffected = rowsAffected;
            this.timeMs       = timeMs;
            this.thread       = thread;
        }
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static Builder builder(String scenario) {
        return new Builder(scenario);
    }

    public static class Builder {
        private final String         scenario;
        private       String         outcome = "UNKNOWN";
        private final List<String>   steps   = new ArrayList<>();
        private final List<LabTrace> sqlTrace = new ArrayList<>();

        Builder(String scenario) { this.scenario = scenario; }

        public Builder outcome(String outcome)   { this.outcome = outcome; return this; }

        public Builder step(String msg)          { steps.add(msg);          return this; }

        /**
         * Records a SQL statement with timing. Thread-safe – synchronized on
         * the list for multi-threaded labs.
         */
        public synchronized Builder trace(String sql, List<String> params,
                                          int rowsAffected, long timeMs) {
            sqlTrace.add(new LabTrace(sql, params, rowsAffected, timeMs,
                    Thread.currentThread().getName()));
            return this;
        }

        public LabResult build() { return new LabResult(this); }
    }
}
