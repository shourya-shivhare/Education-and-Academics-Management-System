package edu.dbms.education.api;

import edu.dbms.education.trace.SqlTraceEntry;
import lombok.Getter;

import java.util.List;

/**
 * Standard API response wrapper used by every endpoint.
 *
 * Shape:
 * {
 *   "data":  <any payload or null>,
 *   "trace": [ { "sql": "...", "params": [...], "rowsAffected": N, "timeMs": M }, ... ]
 * }
 *
 * DBMS concept: the trace array exposes every parameterised SQL statement
 * executed during the request, including stored procedure CALL statements.
 * This makes the database activity fully transparent for the coursework demo.
 *
 * Error responses use the same envelope with data = null and an added
 * "error" field (see GlobalExceptionHandler).
 */
@Getter
public class ResponseEnvelope<T> {

    private final T                  data;
    private final List<SqlTraceEntry> trace;

    public ResponseEnvelope(T data, List<SqlTraceEntry> trace) {
        this.data  = data;
        this.trace = trace == null ? List.of() : trace;
    }

    /** Convenience factory for successful responses. */
    public static <T> ResponseEnvelope<T> ok(T data, List<SqlTraceEntry> trace) {
        return new ResponseEnvelope<>(data, trace);
    }
}
