package edu.dbms.education.lab;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thin JdbcTemplate wrapper used exclusively by lab scenarios.
 *
 * Why not use TracingJdbcTemplate?
 *   TracingJdbcTemplate injects the request-scoped SqlTraceService.
 *   Multi-threaded lab scenarios (race, deadlock, isolation) run SQL on
 *   background threads where the Spring request scope is not active, causing
 *   a ScopeNotActiveException.  LabJdbc records into LabResult.Builder instead,
 *   which is a plain thread-safe object with no scope dependency.
 *
 * Each lab scenario creates its own LabJdbc with its own connection, so the
 * isolation level can be set per-scenario without affecting the app pool.
 *
 * Auto-commit behaviour:
 *   setAutoCommit(false) is managed explicitly inside each scenario so the
 *   lab code exactly mirrors what a DBA would type in a MySQL client.
 */
public class LabJdbc {

    private final JdbcTemplate        jdbc;
    private final LabResult.Builder   log;

    public LabJdbc(DataSource dataSource, LabResult.Builder log) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.log  = log;
    }

    // ── Raw JdbcTemplate access (for SET SESSION, START TRANSACTION etc.) ──

    public JdbcTemplate raw() { return jdbc; }

    // ── Traced SELECT ─────────────────────────────────────────────────────

    public List<Map<String, Object>> query(String sql, Object... params) {
        long t = System.currentTimeMillis();
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        log.trace(sql, toStr(params), rows.size(), ms(t));
        return rows;
    }

    public Map<String, Object> queryOne(String sql, Object... params) {
        long t = System.currentTimeMillis();
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        int n = rows.isEmpty() ? 0 : 1;
        log.trace(sql, toStr(params), n, ms(t));
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public <T> T scalar(String sql, Class<T> type, Object... params) {
        long t = System.currentTimeMillis();
        T val = jdbc.queryForObject(sql, type, params);
        log.trace(sql, toStr(params), 1, ms(t));
        return val;
    }

    // ── Traced DML ────────────────────────────────────────────────────────

    public int update(String sql, Object... params) {
        long t = System.currentTimeMillis();
        int rows = jdbc.update(sql, params);
        log.trace(sql, toStr(params), rows, ms(t));
        return rows;
    }

    // ── Traced transaction control ────────────────────────────────────────

    public void execute(String sql) {
        long t = System.currentTimeMillis();
        jdbc.execute(sql);
        log.trace(sql, List.of(), -1, ms(t));
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private long ms(long start) { return System.currentTimeMillis() - start; }

    private List<String> toStr(Object[] params) {
        if (params == null || params.length == 0) return List.of();
        return Arrays.stream(params)
                .map(p -> p == null ? "null" : p.toString())
                .collect(Collectors.toList());
    }
}
