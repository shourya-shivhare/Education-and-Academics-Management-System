package edu.dbms.education.attendance;

import edu.dbms.education.common.PageRequest;
import edu.dbms.education.trace.TracingJdbcTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DBMS concept: AFTER INSERT trigger side-effect.
 * When we INSERT into attendance, trg_attendance_after_insert fires and
 * automatically updates enrollment.is_detained.  This is transparent to
 * the application layer – we just INSERT and the DB handles the flag.
 *
 * DBMS concept: UNIQUE constraint on (enrollment_id, class_date).
 * A duplicate INSERT raises DataIntegrityViolationException → HTTP 409.
 */
@Repository
@RequiredArgsConstructor
public class AttendanceRepository {

    private final TracingJdbcTemplate jdbc;

    // ── SQL ──────────────────────────────────────────────────────────────

    private static final String INSERT =
            "INSERT INTO attendance (enrollment_id, class_date, status) VALUES (?,?,?)";

    private static final String BASE_SELECT =
            "SELECT a.attendance_id, a.enrollment_id," +
            "  e.student_id, s.roll_no," +
            "  a.class_date, a.status," +
            "  sub.code AS subject_code, co.section AS offering_section" +
            " FROM attendance a" +
            " JOIN enrollment e      ON a.enrollment_id = e.enrollment_id" +
            " JOIN student s         ON e.student_id    = s.student_id" +
            " JOIN course_offering co ON e.offering_id  = co.offering_id" +
            " JOIN subject sub       ON co.subject_id   = sub.subject_id";

    private static final String SELECT_BY_ID = BASE_SELECT + " WHERE a.attendance_id = ?";

    /*
     * Ownership guard: verify that the offering belongs to the requesting faculty
     * before allowing an INSERT.  Used in service before calling create().
     */
    private static final String SQL_OFFERING_FACULTY =
            "SELECT co.faculty_id" +
            " FROM enrollment enr" +
            " JOIN course_offering co ON enr.offering_id = co.offering_id" +
            " WHERE enr.enrollment_id = ?";

    private static final String UPDATE_STATUS =
            "UPDATE attendance SET status = ? WHERE attendance_id = ?";

    private static final String DELETE = "DELETE FROM attendance WHERE attendance_id = ?";

    // ── RowMapper ─────────────────────────────────────────────────────────

    static final RowMapper<AttendanceDto> ROW_MAPPER = (rs, n) -> new AttendanceDto(
            rs.getInt("attendance_id"),
            rs.getInt("enrollment_id"),
            rs.getInt("student_id"),
            rs.getString("roll_no"),
            rs.getDate("class_date").toLocalDate(),
            rs.getString("status"),
            rs.getString("subject_code"),
            rs.getString("offering_section")
    );

    // ── CRUD ─────────────────────────────────────────────────────────────

    public int create(RecordAttendanceRequest r) {
        KeyHolder kh = new GeneratedKeyHolder();
        long t = System.currentTimeMillis();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, r.getEnrollmentId());
            ps.setDate(2, Date.valueOf(r.getClassDate()));
            ps.setString(3, r.getStatus());
            return ps;
        }, kh);
        jdbc.getSqlTraceService().record(INSERT,
                List.of(String.valueOf(r.getEnrollmentId()), r.getClassDate().toString(), r.getStatus()),
                1, System.currentTimeMillis() - t);
        return kh.getKey().intValue();
    }

    public Optional<AttendanceDto> findById(int id) {
        try {
            return Optional.of(jdbc.queryForObjectTraced(SELECT_BY_ID, ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Resolve the faculty_id for the offering that contains this enrollment.
     * Used by the service to enforce FACULTY can only record attendance for
     * their own offerings.
     */
    public Optional<Integer> findOfferingFacultyByEnrollmentId(int enrollmentId) {
        try {
            return Optional.of(jdbc.queryForScalarTraced(
                    SQL_OFFERING_FACULTY, Integer.class, enrollmentId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Filters: enrollmentId, offeringId, studentId, classDate, fromDate/toDate.
     * STUDENT scope: pass studentId = personId.
     * FACULTY scope: pass offeringId(s) from their course_offering rows.
     */
    public List<AttendanceDto> search(Integer enrollmentId, Integer offeringId,
                                      Integer studentId, String fromDate, String toDate,
                                      PageRequest page) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (enrollmentId != null) { sql.append(" AND a.enrollment_id = ?"); params.add(enrollmentId); }
        if (offeringId   != null) { sql.append(" AND co.offering_id  = ?"); params.add(offeringId); }
        if (studentId    != null) { sql.append(" AND e.student_id    = ?"); params.add(studentId); }
        if (fromDate     != null) { sql.append(" AND a.class_date >= ?");    params.add(fromDate); }
        if (toDate       != null) { sql.append(" AND a.class_date <= ?");    params.add(toDate); }
        sql.append(" ORDER BY a.class_date DESC LIMIT ? OFFSET ?");
        params.add(page.getSize()); params.add(page.offset());

        return jdbc.queryTraced(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public void updateStatus(int id, String status) {
        jdbc.updateTraced(UPDATE_STATUS, status, id);
    }

    public void delete(int id) {
        jdbc.updateTraced(DELETE, id);
    }
}
