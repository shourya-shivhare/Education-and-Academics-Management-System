package edu.dbms.education.trace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thin wrapper around JdbcTemplate that records every SQL statement in
 * SqlTraceService before delegating to the standard implementation.
 *
 * DBMS concept: all queries use parameterised form (? placeholders).
 * The SQL template and the parameter values are always kept separate –
 * this is exactly what prevents SQL injection at the protocol level.
 *
 * Design choice – wrapper instead of DataSource proxy:
 *   A DataSource proxy (e.g. p6spy) intercepts at the JDBC driver level and
 *   sees the *rendered* SQL.  A JdbcTemplate wrapper intercepts at the Spring
 *   layer and can record the *template + params* before rendering, which is
 *   more informative for showing parameterised-query concepts.
 *
 * Only the method overloads actually used by this project are overridden.
 * Any method not overridden falls back to the inherited JdbcTemplate behaviour
 * without tracing (acceptable – add overrides as needed).
 */
@Slf4j
@Primary
@Component
@DependsOn("flywayInitializer")  // ensure migrations run before the app datasource is used
public class TracingJdbcTemplate extends JdbcTemplate {

    private final SqlTraceService traceService;

    public TracingJdbcTemplate(
            @Qualifier("appDataSource") DataSource dataSource,
            SqlTraceService traceService) {
        super(dataSource);
        this.traceService = traceService;
    }

    /** Exposes the trace service so repositories using KeyHolder can record manually. */
    public SqlTraceService getSqlTraceService() {
        return traceService;
    }

    // ── SELECT – returns List<Map<String,Object>> ──────────────────────────

    @Override
    public List<Map<String, Object>> queryForList(String sql) {
        long start = System.currentTimeMillis();
        List<Map<String, Object>> result = super.queryForList(sql);
        traceService.record(sql, List.of(), result.size(), ms(start));
        return result;
    }

    /**
     * Primary SELECT with positional parameters.
     * Used by repository methods for all parameterised reads.
     */
    public <T> List<T> queryTraced(String sql, RowMapper<T> rowMapper, Object... params) {
        long start = System.currentTimeMillis();
        List<T> result = super.query(sql, rowMapper, params);
        traceService.record(sql, toStringList(params), result.size(), ms(start));
        return result;
    }

    /**
     * SELECT returning a single object; throws EmptyResultDataAccessException
     * if no row is found (consistent with JdbcTemplate contract).
     */
    public <T> T queryForObjectTraced(String sql, RowMapper<T> rowMapper, Object... params) {
        long start = System.currentTimeMillis();
        T result = super.queryForObject(sql, rowMapper, params);
        traceService.record(sql, toStringList(params), 1, ms(start));
        return result;
    }

    /**
     * SELECT returning a single scalar value (e.g. COUNT, EXISTS).
     */
    public <T> T queryForScalarTraced(String sql, Class<T> requiredType, Object... params) {
        long start = System.currentTimeMillis();
        T result = super.queryForObject(sql, requiredType, params);
        // rowsAffected = 1 (one row, one column)
        traceService.record(sql, toStringList(params), 1, ms(start));
        return result;
    }

    // ── DML – INSERT / UPDATE / DELETE ────────────────────────────────────

    /**
     * INSERT / UPDATE / DELETE with positional parameters.
     * Returns the number of rows affected by the statement.
     */
    public int updateTraced(String sql, Object... params) {
        long start = System.currentTimeMillis();
        int rows = super.update(sql, params);
        traceService.record(sql, toStringList(params), rows, ms(start));
        return rows;
    }

    /**
     * INSERT / UPDATE / DELETE without parameters (rare, but needed for
     * management SQL like TRUNCATE in demo scripts).
     */
    public int updateTraced(String sql) {
        long start = System.currentTimeMillis();
        int rows = super.update(sql);
        traceService.record(sql, List.of(), rows, ms(start));
        return rows;
    }

    // ── CALL – stored procedures ───────────────────────────────────────────

    /**
     * Execute a CALL statement via execute(String, PreparedStatementCallback).
     *
     * DBMS concept: stored procedures encapsulate business logic in MySQL.
     * SimpleJdbcCall is the preferred way to call them with named parameters;
     * this method handles raw CALL strings when SimpleJdbcCall is not used.
     *
     * rowsAffected is -1 because a stored procedure may affect multiple tables
     * internally; the caller should inspect OUT parameters instead.
     */
    public void callTraced(String callSql, Object... params) {
        long start = System.currentTimeMillis();
        super.execute(callSql, (PreparedStatementCallback<Void>) ps -> {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.execute();
            return null;
        });
        // -1 = rows not applicable for a stored procedure call
        traceService.record(callSql, toStringList(params), -1, ms(start));
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private long ms(long startNano) {
        return System.currentTimeMillis() - startNano;
    }

    /**
     * Converts parameter array to a list of safe string representations.
     * null values are rendered as the string "null" so the trace is readable.
     */
    private List<String> toStringList(Object[] params) {
        if (params == null || params.length == 0) {
            return List.of();
        }
        return Arrays.stream(params)
                .map(p -> p == null ? "null" : p.toString())
                .collect(Collectors.toList());
    }
}
