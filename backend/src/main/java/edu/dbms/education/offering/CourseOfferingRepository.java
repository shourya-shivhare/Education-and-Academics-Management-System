package edu.dbms.education.offering;

import edu.dbms.education.common.PageRequest;
import edu.dbms.education.trace.TracingJdbcTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CourseOfferingRepository {

    private final TracingJdbcTemplate jdbc;

    // ── SQL ──────────────────────────────────────────────────────────────

    private static final String INSERT =
            "INSERT INTO course_offering (subject_id, faculty_id, term_id, section, capacity, status)" +
            " VALUES (?,?,?,?,?,'Open')";

    /*
     * enrolled_count is NOT stored in course_offering (see schema comments).
     * It is derived inline as a correlated subquery selecting from enrollment.
     * DBMS concept: derived attribute – compute on read, never store.
     */
    private static final String BASE_SELECT =
            "SELECT co.offering_id, co.subject_id, sub.code AS subject_code, sub.title AS subject_title," +
            "  co.faculty_id, CONCAT(p.first_name,' ',p.last_name) AS faculty_name," +
            "  co.term_id, t.name AS term_name, co.section, co.capacity," +
            "  (SELECT COUNT(*) FROM enrollment e" +
            "   WHERE e.offering_id = co.offering_id AND e.status != 'Dropped') AS enrolled_count," +
            "  co.status" +
            " FROM course_offering co" +
            " JOIN subject sub ON co.subject_id = sub.subject_id" +
            " JOIN faculty f   ON co.faculty_id = f.faculty_id" +
            " JOIN person p    ON f.faculty_id  = p.person_id" +
            " JOIN term t      ON co.term_id    = t.term_id";

    private static final String SELECT_BY_ID = BASE_SELECT + " WHERE co.offering_id = ?";

    private static final String UPDATE_STATUS =
            "UPDATE course_offering SET status = ? WHERE offering_id = ?";

    private static final String UPDATE_CAPACITY =
            "UPDATE course_offering SET capacity = ? WHERE offering_id = ?";

    private static final String DELETE =
            "DELETE FROM course_offering WHERE offering_id = ?";

    // ── RowMapper ─────────────────────────────────────────────────────────

    static final RowMapper<CourseOfferingDto> ROW_MAPPER = (rs, n) -> new CourseOfferingDto(
            rs.getInt("offering_id"),
            rs.getInt("subject_id"),
            rs.getString("subject_code"),
            rs.getString("subject_title"),
            rs.getInt("faculty_id"),
            rs.getString("faculty_name"),
            rs.getInt("term_id"),
            rs.getString("term_name"),
            rs.getString("section"),
            rs.getInt("capacity"),
            rs.getInt("enrolled_count"),
            rs.getString("status")
    );

    // ── CRUD ─────────────────────────────────────────────────────────────

    public int create(CreateOfferingRequest r) {
        KeyHolder kh = new GeneratedKeyHolder();
        long t = System.currentTimeMillis();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, r.getSubjectId());
            ps.setInt(2, r.getFacultyId());
            ps.setInt(3, r.getTermId());
            ps.setString(4, r.getSection());
            ps.setInt(5, r.getCapacity());
            return ps;
        }, kh);
        jdbc.getSqlTraceService().record(INSERT,
                List.of(String.valueOf(r.getSubjectId()), String.valueOf(r.getFacultyId()),
                        r.getSection()), 1, System.currentTimeMillis() - t);
        return kh.getKey().intValue();
    }

    public Optional<CourseOfferingDto> findById(int id) {
        try {
            return Optional.of(jdbc.queryForObjectTraced(SELECT_BY_ID, ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Filters: subjectId, facultyId, termId, status, departmentId.
     * FACULTY scope: when facultyId is passed it restricts to that faculty's offerings.
     */
    public List<CourseOfferingDto> search(Integer subjectId, Integer facultyId,
                                          Integer termId, String status,
                                          Integer departmentId, PageRequest page) {
        StringBuilder sql = new StringBuilder(BASE_SELECT +
                " JOIN department d ON sub.department_id = d.department_id WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (subjectId    != null) { sql.append(" AND co.subject_id  = ?"); params.add(subjectId); }
        if (facultyId    != null) { sql.append(" AND co.faculty_id  = ?"); params.add(facultyId); }
        if (termId       != null) { sql.append(" AND co.term_id     = ?"); params.add(termId); }
        if (status       != null && !status.isBlank()) {
            sql.append(" AND co.status = ?"); params.add(status);
        }
        if (departmentId != null) { sql.append(" AND d.department_id = ?"); params.add(departmentId); }

        sql.append(" ORDER BY t.name, sub.code LIMIT ? OFFSET ?");
        params.add(page.getSize()); params.add(page.offset());

        return jdbc.queryTraced(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public void updateStatus(int id, String status) {
        jdbc.updateTraced(UPDATE_STATUS, status, id);
    }

    public void updateCapacity(int id, int capacity) {
        jdbc.updateTraced(UPDATE_CAPACITY, capacity, id);
    }

    public void delete(int id) {
        jdbc.updateTraced(DELETE, id);
    }
}
