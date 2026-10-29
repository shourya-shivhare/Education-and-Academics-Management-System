package edu.dbms.education.trace;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Request-scoped store for SQL trace entries.
 *
 * DBMS concept: every statement sent to MySQL is a prepared statement with
 * separate parameters. This service collects each entry so the API response
 * can include the full SQL trace, making the database activity completely
 * transparent.
 *
 * @RequestScope – a new instance is created per HTTP request, so entries
 * from concurrent requests never mix.  Spring disposes the bean when the
 * request lifecycle ends.
 *
 * @RequestScope uses proxyMode = ScopedProxyMode.TARGET_CLASS by default.
 * This allows the singleton TracingJdbcTemplate to hold a proxy reference
 * that delegates to the correct per-request SqlTraceService at call time.
 */
@Component
@RequestScope
public class SqlTraceService {

    // Mutable list; only the thread handling this request ever touches it
    private final List<SqlTraceEntry> entries = new ArrayList<>();

    /**
     * Record a completed SQL statement.
     *
     * @param sql          the SQL template with ? placeholders
     * @param params       bound parameter values (may be null → empty list)
     * @param rowsAffected rows returned / changed; pass -1 when not applicable
     * @param timeMs       elapsed milliseconds
     */
    public void record(String sql, List<String> params, int rowsAffected, long timeMs) {
        entries.add(new SqlTraceEntry(
                sql,
                params == null ? List.of() : Collections.unmodifiableList(params),
                rowsAffected,
                timeMs
        ));
    }

    /** Returns an unmodifiable snapshot of the trace for this request. */
    public List<SqlTraceEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /** Clears all entries (useful if a controller re-uses the scope in one request). */
    public void clear() {
        entries.clear();
    }
}
