package edu.dbms.education.enrollment;

import edu.dbms.education.common.PageRequest;
import edu.dbms.education.trace.TracingJdbcTemplate;
import edu.dbms.education.trace.TracingSimpleJdbcCall;
import edu.dbms.education.trace.SqlTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DBMS concept: stored-procedure transactions with SELECT...FOR UPDATE.
 *
 * Enrollment and drop always go through sp_enroll_student / sp_drop_enrollment.
 * These procedures:
 *   1. Acquire a row-level exclusive lock on course_offering (FOR UPDATE).
 *   2. Check business rules (capacity, status, duplicate).
 *   3. INSERT into enrollment (triggering trg_enrollment_before_insert).
 *   4. COMMIT or ROLLBACK atomically.
 *
 * This prevents the "last-seat race condition" where two concurrent threads
 * both read capacity=1, both pass the check, and both enroll – overbooking.
 */
@Repository
@RequiredArgsConstructor
public class EnrollmentRepository {

    private final TracingJdbcTemplate jdbc;
    private final SqlTraceService     traceService;

    // ── SQL ──────────────────────────────────────────────────────────────

    private static final String BASE_SELECT =
            "SELECT e.enrollment_id, e.student_id, s.roll_no," +
            "  CONCAT(p.first_name,' ',p.last_name) AS student_name," +
            "  e.offering_id, sub.code AS subject_code, sub.title AS subject_title," +
            "  t.name AS term_name, e.enroll_date, e.status," +
            "  e.final_marks, e.grade, e.is_detained" +
            " FROM enrollment e" +
            " JOIN student s       ON e.student_id   = s.student_id" +
            " JOIN person p        ON s.student_id   = p.person_id" +
            " JOIN course_offering co ON e.offering_id  = co.offering_id" +
            " JOIN subject sub     ON co.subject_id  = sub.subject_id" +
            " JOIN term t          ON co.term_id     = t.term_id";

    private static final String SELECT_BY_ID = BASE_SELECT + " WHERE e.enrollment_id = ?";

    // ── RowMapper ─────────────────────────────────────────────────────────

    static final RowMapper<EnrollmentDto> ROW_MAPPER = (rs, n) -> new EnrollmentDto(
            rs.getInt("enrollment_id"),
            rs.getInt("student_id"),
            rs.getString("roll_no"),
            rs.getString("student_name"),
            rs.getInt("offering_id"),
            rs.getString("subject_code"),
            rs.getString("subject_title"),
            rs.getString("term_name"),
            rs.getDate("enroll_date").toLocalDate(),
            rs.getString("status"),
            rs.getBigDecimal("final_marks"),
            rs.getString("grade"),
            rs.getBoolean("is_detained")
    );

    // ── Stored procedure calls ────────────────────────────────────────────

    /**
     * Calls sp_enroll_student via TracingSimpleJdbcCall.
     * The CALL statement and parameters appear in the SqlTrace.
     * Any SIGNAL 45000 from the procedure or its triggers propagates as
     * UncategorizedSQLException and is caught by GlobalExceptionHandler → HTTP 409.
     */
    public void enroll(int studentId, int offeringId) {
        TracingSimpleJdbcCall.forProcedure(jdbc, traceService, "sp_enroll_student")
                .execute(new MapSqlParameterSource()
                        .addValue("p_student_id",  studentId)
                        .addValue("p_offering_id", offeringId));
    }

    /**
     * Calls sp_drop_enrollment.
     * SIGNAL 45000 if no active enrollment found → HTTP 409.
     */
    public void drop(int studentId, int offeringId) {
        TracingSimpleJdbcCall.forProcedure(jdbc, traceService, "sp_drop_enrollment")
                .execute(new MapSqlParameterSource()
                        .addValue("p_student_id",  studentId)
                        .addValue("p_offering_id", offeringId));
    }

    public Optional<EnrollmentDto> findById(int id) {
        try {
            return Optional.of(jdbc.queryForObjectTraced(SELECT_BY_ID, ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Filters: studentId (own record scoping), offeringId, termId, status.
     * All clauses use ? bind values – never string interpolation.
     */
    public List<EnrollmentDto> search(Integer studentId, Integer offeringId,
                                      Integer termId, String status, PageRequest page) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (studentId  != null) { sql.append(" AND e.student_id  = ?"); params.add(studentId); }
        if (offeringId != null) { sql.append(" AND e.offering_id = ?"); params.add(offeringId); }
        if (termId     != null) { sql.append(" AND co.term_id    = ?"); params.add(termId); }
        if (status != null && !status.isBlank()) {
            sql.append(" AND e.status = ?"); params.add(status);
        }
        sql.append(" ORDER BY e.enroll_date DESC LIMIT ? OFFSET ?");
        params.add(page.getSize()); params.add(page.offset());

        return jdbc.queryTraced(sql.toString(), ROW_MAPPER, params.toArray());
    }
}
