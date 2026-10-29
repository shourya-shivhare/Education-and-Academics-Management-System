package edu.dbms.education.trace;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * Immutable record of one SQL statement execution.
 *
 * DBMS concept: transparency / observability of parameterised queries.
 * Showing params separately from the SQL text is exactly how prepared
 * statements work at the protocol level – the SQL template and the bind
 * values are distinct, preventing SQL injection.
 *
 * Fields
 *   sql          – the SQL template (? placeholders for positional params)
 *   params       – bind values in order (null-safe, shown as strings)
 *   rowsAffected – rows returned (SELECT) or rows changed (DML); -1 if N/A
 *   timeMs       – wall-clock milliseconds for the statement
 */
@Getter
@AllArgsConstructor
@ToString
public class SqlTraceEntry {

    private final String       sql;
    private final List<String> params;       // bind values as strings (safe to log)
    private final int          rowsAffected; // -1 means "not applicable" (e.g. CALL)
    private final long         timeMs;
}
