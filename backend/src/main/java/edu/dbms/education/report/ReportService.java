package edu.dbms.education.report;

import edu.dbms.education.trace.TracingJdbcTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Orchestrates named-query execution and the four dedicated report queries.
 *
 * All SQL is read-only (SELECT), so @Transactional(readOnly = true) is used
 * throughout to tell the connection pool / InnoDB to avoid setting the redo-log
 * dirty bit and to allow load-balancer read-replica routing if added later.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final QueryRegistry       registry;
    private final ReportRunner        runner;
    private final TracingJdbcTemplate jdbc;

    // ── Named-query catalogue ─────────────────────────────────────────────

    public List<QueryDefinition> listAll() {
        return registry.all();
    }

    /**
     * Executes one of the 22 named queries by id with caller-supplied parameters.
     *
     * Parameter coercion: values from the JSON body arrive as Strings.
     * We coerce them to the declared type from ParamDef.type so that MySQL
     * receives Integer / Long / BigDecimal bind values rather than plain
     * strings – this matters for query-plan index usage.
     *
     * @param id     query id, e.g. "q01"
     * @param params positional parameter values in declaration order
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> run(String id, List<Object> params) {
        QueryDefinition def = registry.find(id)
                .orElseThrow(() -> new NoSuchElementException("Report not found: " + id));

        // Coerce each parameter to the declared type
        Object[] coerced = coerceParams(def, params);

        return runner.run(def.getSql(), coerced);
    }

    // ── Dedicated reports ─────────────────────────────────────────────────

    /**
     * r01 – Student transcript via sp_generate_transcript view.
     *
     * DBMS concept: the view v_student_transcript pre-joins 8 tables.
     * The query plan for this SELECT is simpler than q01 because the join
     * is already encoded in the view definition and the query planner can
     * treat it as a derived relation.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> transcript(int studentId) {
        String sql =
                "SELECT student_id, roll_no, student_name, program_name," +
                "  term_name, term_start_date, subject_code, subject_title," +
                "  credits, instructor_name, final_marks, grade," +
                "  grade_point, quality_points" +
                " FROM v_student_transcript" +
                " WHERE student_id = ?" +
                " ORDER BY term_start_date, subject_code";
        return jdbc.queryTraced(sql, new ColumnMapRowMapper(), studentId);
    }

    /**
     * r02 – Attendance shortage list (students below 75% in any offering).
     *
     * DBMS concept: v_attendance_summary encapsulates the GROUP BY aggregation.
     * The WHERE predicate pushes is_shortage = TRUE as a filter on the view –
     * MySQL merges it into the underlying GROUP BY query.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> attendanceShortage(Integer termId) {
        StringBuilder sql = new StringBuilder(
                "SELECT student_id, roll_no, student_name," +
                "  offering_id, subject_code, subject_title, term_name," +
                "  classes_held, classes_attended, classes_absent, attendance_percent" +
                " FROM v_attendance_summary" +
                " WHERE is_shortage = TRUE");

        if (termId != null) {
            sql.append(" AND term_id = ?");
            return jdbc.queryTraced(sql.toString(), new ColumnMapRowMapper(), termId);
        }
        return jdbc.queryTraced(sql.toString(), new ColumnMapRowMapper());
    }

    /**
     * r03 – Toppers: top N students per department by average final marks.
     *
     * DBMS concept: window function DENSE_RANK() over PARTITION BY department_id.
     * Filtering dense_rank <= topN in the outer WHERE is done after the window
     * function is evaluated – demonstrating that window predicates cannot go
     * in the same SELECT's WHERE clause and require a wrapping derived table.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> toppers(int topN) {
        String sql =
                "SELECT department_name, roll_no, student_name," +
                "  student_gpa, dense_rank_in_dept AS rank_in_dept" +
                " FROM (" +
                "  SELECT d.name AS department_name," +
                "    s.roll_no," +
                "    CONCAT(p.first_name,' ',p.last_name) AS student_name," +
                "    ROUND(AVG(e.final_marks), 2) AS student_gpa," +
                "    DENSE_RANK() OVER (" +
                "      PARTITION BY d.department_id" +
                "      ORDER BY AVG(e.final_marks) DESC" +
                "    ) AS dense_rank_in_dept" +
                "  FROM student s" +
                "  JOIN person p          ON s.student_id   = p.person_id" +
                "  JOIN program prog      ON s.program_id   = prog.program_id" +
                "  JOIN department d      ON prog.department_id = d.department_id" +
                "  JOIN enrollment e      ON s.student_id   = e.student_id" +
                "  WHERE e.final_marks IS NOT NULL" +
                "  GROUP BY d.department_id, d.name, s.student_id, s.roll_no," +
                "           p.first_name, p.last_name" +
                " ) ranked" +
                " WHERE dense_rank_in_dept <= ?" +
                " ORDER BY department_name, dense_rank_in_dept";
        return jdbc.queryTraced(sql, new ColumnMapRowMapper(), topN);
    }

    /**
     * r04 – Department academic performance dashboard.
     *
     * DBMS concept: v_dept_performance is a non-updatable aggregate view
     * (GROUP BY + AVG) – reads it directly, optionally filtered by departmentId.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> departmentPerformance(Integer departmentId) {
        if (departmentId != null) {
            return jdbc.queryTraced(
                    "SELECT * FROM v_dept_performance WHERE department_id = ?",
                    new ColumnMapRowMapper(), departmentId);
        }
        return jdbc.queryTraced(
                "SELECT * FROM v_dept_performance ORDER BY avg_final_marks DESC",
                new ColumnMapRowMapper());
    }

    // ── Parameter coercion ────────────────────────────────────────────────

    /**
     * Coerces caller-supplied parameter values (which may arrive as strings from
     * the JSON request body) to the types declared in the ParamDef list.
     *
     * DBMS concept: sending correctly-typed bind values lets the MySQL query
     * planner use numeric indexes (range scans) rather than treating the value
     * as a string and falling back to a full-scan type conversion.
     */
    private Object[] coerceParams(QueryDefinition def, List<Object> rawParams) {
        List<QueryDefinition.ParamDef> paramDefs = def.getParams();
        if (paramDefs == null || paramDefs.isEmpty()) {
            return new Object[0];
        }
        int size = Math.min(paramDefs.size(), rawParams == null ? 0 : rawParams.size());
        Object[] result = new Object[paramDefs.size()];

        for (int i = 0; i < paramDefs.size(); i++) {
            Object raw = (rawParams != null && i < rawParams.size())
                    ? rawParams.get(i)
                    : paramDefs.get(i).getDefaultValue();  // fall back to default

            result[i] = switch (paramDefs.get(i).getType()) {
                case "INTEGER" -> raw == null ? null : Long.parseLong(raw.toString());
                case "DECIMAL" -> raw == null ? null
                        : new java.math.BigDecimal(raw.toString());
                default        -> raw == null ? null : raw.toString();  // STRING
            };
        }
        return result;
    }
}
