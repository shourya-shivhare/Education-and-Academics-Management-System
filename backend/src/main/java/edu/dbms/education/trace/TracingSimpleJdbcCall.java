package edu.dbms.education.trace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory / wrapper that creates a {@link SimpleJdbcCall} and records its
 * invocation in {@link SqlTraceService}.
 *
 * DBMS concept: stored procedures are a first-class object in MySQL.
 * SimpleJdbcCall reads the procedure's IN/OUT parameter metadata from the
 * information_schema at first call (cached per procedure name), then sends
 * a fully parameterised CALL statement – no string concatenation.
 *
 * Usage (in a repository):
 * <pre>
 *   Map&lt;String,Object&gt; out = TracingSimpleJdbcCall
 *       .forProcedure(jdbcTemplate, traceService, "enroll_student")
 *       .execute(new MapSqlParameterSource()
 *           .addValue("p_student_id", studentId)
 *           .addValue("p_section_id", sectionId));
 * </pre>
 */
@Slf4j
public class TracingSimpleJdbcCall {

    private final SimpleJdbcCall  delegate;
    private final SqlTraceService traceService;
    private final String          procedureName;

    private TracingSimpleJdbcCall(SimpleJdbcCall delegate,
                                   SqlTraceService traceService,
                                   String procedureName) {
        this.delegate      = delegate;
        this.traceService  = traceService;
        this.procedureName = procedureName;
    }

    /**
     * Creates a TracingSimpleJdbcCall for a stored procedure.
     *
     * @param jdbcTemplate  the app-user JdbcTemplate (or TracingJdbcTemplate)
     * @param traceService  request-scoped trace collector
     * @param procedureName MySQL procedure name (case-insensitive on most systems)
     */
    public static TracingSimpleJdbcCall forProcedure(JdbcTemplate jdbcTemplate,
                                                      SqlTraceService traceService,
                                                      String procedureName) {
        SimpleJdbcCall call = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName(procedureName);
        return new TracingSimpleJdbcCall(call, traceService, procedureName);
    }

    /**
     * Executes the procedure and records the CALL in the trace.
     *
     * @param inParams  IN parameter values keyed by parameter name
     * @return          OUT / INOUT parameter values (empty map if none)
     */
    public Map<String, Object> execute(org.springframework.jdbc.core.namedparam.MapSqlParameterSource inParams) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = delegate.execute(inParams);
        long elapsed = System.currentTimeMillis() - start;

        // Build a readable CALL representation for the trace
        String callSql = buildCallSql(inParams.getValues());
        java.util.List<String> paramList = inParams.getValues().entrySet().stream()
                .map(e -> e.getKey() + "=" + (e.getValue() == null ? "null" : e.getValue().toString()))
                .collect(java.util.stream.Collectors.toList());

        // rowsAffected = -1: stored procedures may affect many tables internally
        traceService.record(callSql, paramList, -1, elapsed);

        return result;
    }

    /**
     * Executes the procedure with a plain Map of parameters.
     */
    public Map<String, Object> execute(Map<String, Object> inParams) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = delegate.execute(inParams);
        long elapsed = System.currentTimeMillis() - start;

        String callSql = buildCallSql(inParams);
        java.util.List<String> paramList = inParams.entrySet().stream()
                .map(e -> e.getKey() + "=" + (e.getValue() == null ? "null" : e.getValue().toString()))
                .collect(Collectors.toList());

        traceService.record(callSql, paramList, -1, elapsed);
        return result;
    }

    // Builds a human-readable CALL string: CALL proc_name(:p1, :p2)
    private String buildCallSql(Map<String, ?> params) {
        String args = params.keySet().stream()
                .map(k -> ":" + k)
                .collect(Collectors.joining(", "));
        return "CALL " + procedureName + "(" + args + ")";
    }
}
