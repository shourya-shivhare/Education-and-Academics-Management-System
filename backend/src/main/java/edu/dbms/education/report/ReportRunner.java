package edu.dbms.education.report;

import edu.dbms.education.trace.TracingJdbcTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Executes a named query with caller-supplied bind values.
 *
 * DBMS concept: parameterised execution.
 * The SQL template from queries.json contains only ? placeholders.
 * The values array travels to MySQL as a separate bind-parameters message
 * (PreparedStatement protocol), completely decoupled from the SQL text.
 * This is structurally injection-proof regardless of the value content.
 *
 * The SELECT console (SqlConsoleService) uses its own validator before
 * reaching this runner – this class itself does not impose a SELECT-only
 * restriction so that future DML report queries could use it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportRunner {

    private final TracingJdbcTemplate jdbc;

    /**
     * Executes a parameterised SQL template and returns rows as a list of
     * column-name → value maps. Each row is an ordered LinkedHashMap with
     * column names from the result-set metadata as keys.
     *
     * @param sql    SQL template with ? positional placeholders
     * @param params bind values in declaration order (may be empty)
     * @return       list of rows; empty list if no data, never null
     */
    public List<Map<String, Object>> run(String sql, Object[] params) {
        log.debug("ReportRunner.run: {} params for SQL starting: {}",
                params.length, sql.substring(0, Math.min(60, sql.length())));

        // queryTraced records the SQL + params in SqlTraceService so the trace
        // appears in the API response envelope like every other statement.
        return jdbc.queryTraced(sql, new ColumnMapRowMapper(), params);
    }
}
