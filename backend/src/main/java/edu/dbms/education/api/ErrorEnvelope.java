package edu.dbms.education.api;

import edu.dbms.education.trace.SqlTraceEntry;
import lombok.Getter;

import java.util.List;

/**
 * Error variant of the API envelope.
 *
 * Shape:
 * {
 *   "data":    null,
 *   "error":   { "code": "...", "message": "..." },
 *   "trace":   [ ... ]
 * }
 *
 * The trace is included even in error responses so callers can see exactly
 * which SQL statement triggered the error (e.g. a MySQL SIGNAL 45000).
 */
@Getter
public class ErrorEnvelope {

    private final Object             data  = null;
    private final ErrorBody          error;
    private final List<SqlTraceEntry> trace;

    public ErrorEnvelope(String code, String message, List<SqlTraceEntry> trace) {
        this.error = new ErrorBody(code, message);
        this.trace = trace == null ? List.of() : trace;
    }

    @Getter
    public static class ErrorBody {
        private final String code;
        private final String message;

        public ErrorBody(String code, String message) {
            this.code    = code;
            this.message = message;
        }
    }
}
