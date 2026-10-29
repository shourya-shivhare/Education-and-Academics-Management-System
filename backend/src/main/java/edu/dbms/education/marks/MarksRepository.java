package edu.dbms.education.marks;

import edu.dbms.education.common.ConflictException;
import edu.dbms.education.common.PageRequest;
import edu.dbms.education.trace.TracingJdbcTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DBMS concept: optimistic concurrency control.
 *
 * The marks table has a `version INT NOT NULL DEFAULT 1` column.
 * Instead of locking the row (pessimistic), we verify that no concurrent
 * writer committed between our read and our write:
 *
 *   UPDATE marks
 *   SET marks_obtained = ?, version = version + 1
 *   WHERE enrollment_id = ? AND offering_id = ? AND assessment_no = ? AND version = ?
 *
 * If another transaction committed first, version will have been incremented
 * and our WHERE clause matches 0 rows.  We detect this via ROW_COUNT() = 0
 * and throw ConflictException → HTTP 409.
 *
 * The marks table has no surrogate PK – its composite PK is
 * (enrollment_id, offering_id, assessment_no).  All DML uses all three.
 *
 * DBMS concept: trg_marks_after_update fires after every UPDATE, writing
 * an audit_log row with OLD/NEW values as JSON – transparent to this layer.
 * trg_marks_before_insert/update validates bounds against assessment.max_marks.
 */
@Repository
@RequiredArgsConstructor
public class MarksRepository {

    private final TracingJdbcTemplate jdbc;

    // ── SQL ──────────────────────────────────────────────────────────────

    // INSERT – DB sets version = 1 by DEFAULT
    private static final String INSERT =
            "INSERT INTO marks (enrollment_id, offering_id, assessment_no, marks_obtained)" +
            " VALUES (?,?,?,?)";

    /*
     * Optimistic-lock UPDATE.
     * version = version + 1 is computed by the DB, not sent from Java,
     * so the bind value list has only the 5 visible ? below:
     *   1. marks_obtained (new value)
     *   2. enrollment_id  (PK part 1)
     *   3. offering_id    (PK part 2)
     *   4. assessment_no  (PK part 3)
     *   5. version        (expected current version – the lock predicate)
     */
    private static final String UPDATE_OPTIMISTIC =
            "UPDATE marks" +
            " SET marks_obtained = ?, version = version + 1" +
            " WHERE enrollment_id = ? AND offering_id = ? AND assessment_no = ? AND version = ?";

    private static final String BASE_SELECT =
            "SELECT m.enrollment_id, m.offering_id, m.assessment_no," +
            "  a.title AS assessment_title, a.max_marks, a.weightage," +
            "  e.student_id, s.roll_no," +
            "  m.marks_obtained, m.version" +
            " FROM marks m" +
            " JOIN assessment a  ON m.offering_id   = a.offering_id" +
            "                   AND m.assessment_no  = a.assessment_no" +
            " JOIN enrollment e  ON m.enrollment_id  = e.enrollment_id" +
            " JOIN student s     ON e.student_id     = s.student_id";

    private static final String SELECT_BY_PK =
            BASE_SELECT +
            " WHERE m.enrollment_id = ? AND m.offering_id = ? AND m.assessment_no = ?";

    // Ownership guard: verify the offering belongs to the requesting faculty
    private static final String SQL_OFFERING_FACULTY =
            "SELECT faculty_id FROM course_offering WHERE offering_id = ?";

    // ── RowMapper ─────────────────────────────────────────────────────────

    static final RowMapper<MarksDto> ROW_MAPPER = (rs, n) -> new MarksDto(
            rs.getInt("enrollment_id"),
            rs.getInt("offering_id"),
            rs.getInt("assessment_no"),
            rs.getString("assessment_title"),
            rs.getInt("max_marks"),
            rs.getInt("weightage"),
            rs.getInt("student_id"),
            rs.getString("roll_no"),
            rs.getBigDecimal("marks_obtained"),
            rs.getInt("version")
    );

    // ── CRUD ─────────────────────────────────────────────────────────────

    public void insert(UpsertMarksRequest r) {
        // INSERT – trg_marks_before_insert validates bounds at DB level
        jdbc.updateTraced(INSERT,
                r.getEnrollmentId(), r.getOfferingId(),
                r.getAssessmentNo(), r.getMarksObtained());
    }

    /**
     * Optimistic-lock update.
     *
     * Sends the version the client last saw. If 0 rows are affected,
     * someone else already committed a newer version → throw ConflictException.
     *
     * The trigger trg_marks_before_update re-checks bounds; if marks > max it
     * SIGNALs SQLSTATE '45000' (GlobalExceptionHandler → HTTP 409).
     */
    public void updateOptimistic(UpsertMarksRequest r) {
        int rows = jdbc.updateTraced(UPDATE_OPTIMISTIC,
                r.getMarksObtained(),
                r.getEnrollmentId(), r.getOfferingId(), r.getAssessmentNo(),
                r.getVersion());           // expected version (lock predicate)

        if (rows == 0) {
            throw new ConflictException(
                    "Marks were modified by another user. " +
                    "Refresh and retry. (enrollment=" + r.getEnrollmentId() +
                    ", offering=" + r.getOfferingId() +
                    ", assessment=" + r.getAssessmentNo() + ")");
        }
    }

    public Optional<MarksDto> findByPk(int enrollmentId, int offeringId, int assessmentNo) {
        try {
            return Optional.of(jdbc.queryForObjectTraced(
                    SELECT_BY_PK, ROW_MAPPER, enrollmentId, offeringId, assessmentNo));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Filters: offeringId (required for FACULTY scope), enrollmentId, studentId.
     * FACULTY should always pass offeringId to scope to their course.
     * STUDENT should always pass studentId (= personId) to see only their marks.
     */
    public List<MarksDto> search(Integer offeringId, Integer enrollmentId,
                                 Integer studentId, PageRequest page) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (offeringId   != null) { sql.append(" AND m.offering_id    = ?"); params.add(offeringId); }
        if (enrollmentId != null) { sql.append(" AND m.enrollment_id  = ?"); params.add(enrollmentId); }
        if (studentId    != null) { sql.append(" AND e.student_id     = ?"); params.add(studentId); }
        sql.append(" ORDER BY m.offering_id, m.assessment_no LIMIT ? OFFSET ?");
        params.add(page.getSize()); params.add(page.offset());

        return jdbc.queryTraced(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public Optional<Integer> findOfferingFaculty(int offeringId) {
        try {
            return Optional.of(jdbc.queryForScalarTraced(
                    SQL_OFFERING_FACULTY, Integer.class, offeringId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
