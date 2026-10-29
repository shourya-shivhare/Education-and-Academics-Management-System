package edu.dbms.education.student;

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
 * DBMS concept: IS-A (total participation).
 * student.student_id is NOT auto-increment – it equals person.person_id.
 * Create sequence: INSERT person → capture auto-generated person_id →
 * INSERT student with that same id.  Both in one @Transactional service call.
 */
@Repository
@RequiredArgsConstructor
public class StudentRepository {

    private final TracingJdbcTemplate jdbc;

    // ── SQL constants ─────────────────────────────────────────────────────

    private static final String INSERT_PERSON =
            "INSERT INTO person (first_name, last_name, email, date_of_birth, gender," +
            " address_line, city, state, pin_code) VALUES (?,?,?,?,?,?,?,?,?)";

    private static final String INSERT_STUDENT =
            "INSERT INTO student (student_id, roll_no, program_id, admission_date, current_semester)" +
            " VALUES (?,?,?,?,?)";

    private static final String BASE_SELECT =
            "SELECT p.person_id, p.first_name, p.last_name, p.email, p.date_of_birth, p.gender," +
            "  s.roll_no, s.program_id, pr.name AS program_name," +
            "  s.admission_date, s.status, s.current_semester" +
            " FROM student s" +
            " JOIN person p  ON s.student_id = p.person_id" +
            " JOIN program pr ON s.program_id = pr.program_id";

    private static final String SELECT_BY_ID = BASE_SELECT + " WHERE s.student_id = ?";

    private static final String UPDATE_PERSON =
            "UPDATE person SET address_line = ?, city = ?, state = ?, pin_code = ? WHERE person_id = ?";

    private static final String UPDATE_STUDENT =
            "UPDATE student SET status = ?, current_semester = ? WHERE student_id = ?";

    // Hard-delete is blocked by trg_student_before_delete if enrollments/fees exist
    private static final String DELETE_STUDENT  = "DELETE FROM student  WHERE student_id = ?";
    private static final String DELETE_PERSON   = "DELETE FROM person   WHERE person_id  = ?";

    // ── RowMapper ─────────────────────────────────────────────────────────

    static final RowMapper<StudentDto> ROW_MAPPER = (rs, n) -> new StudentDto(
            rs.getInt("person_id"),
            rs.getString("first_name"),
            rs.getString("last_name"),
            rs.getString("email"),
            rs.getDate("date_of_birth").toLocalDate(),
            rs.getString("gender"),
            rs.getString("roll_no"),
            rs.getInt("program_id"),
            rs.getString("program_name"),
            rs.getDate("admission_date").toLocalDate(),
            rs.getString("status"),
            rs.getInt("current_semester")
    );

    // ── CRUD ──────────────────────────────────────────────────────────────

    /**
     * Two-step INSERT: person (auto-PK) then student (same id).
     * Returns the new student_id (= person_id).
     *
     * Spring's KeyHolder captures LAST_INSERT_ID() after the person INSERT.
     * We record the INSERT_PERSON statement via the KeyHolder path which does
     * not go through updateTraced – manually add a trace entry via a wrapper.
     */
    public int create(CreateStudentRequest r) {
        // Step 1: INSERT person – need generated key
        KeyHolder keyHolder = new GeneratedKeyHolder();
        long start = System.currentTimeMillis();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT_PERSON, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, r.getFirstName());
            ps.setString(2, r.getLastName());
            ps.setString(3, r.getEmail());
            ps.setDate(4, Date.valueOf(r.getDateOfBirth()));
            ps.setString(5, r.getGender());
            ps.setString(6, r.getAddressLine());
            ps.setString(7, r.getCity());
            ps.setString(8, r.getState());
            ps.setString(9, r.getPinCode());
            return ps;
        }, keyHolder);
        long elapsed = System.currentTimeMillis() - start;
        jdbc.getSqlTraceService().record(INSERT_PERSON,
                List.of(r.getFirstName(), r.getLastName(), r.getEmail()),
                1, elapsed);

        int personId = keyHolder.getKey().intValue();

        // Step 2: INSERT student with same id (IS-A, non-auto-increment PK)
        jdbc.updateTraced(INSERT_STUDENT,
                personId, r.getRollNo(), r.getProgramId(),
                Date.valueOf(r.getAdmissionDate()), r.getCurrentSemester());

        return personId;
    }

    public Optional<StudentDto> findById(int id) {
        try {
            return Optional.of(jdbc.queryForObjectTraced(SELECT_BY_ID, ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Dynamic filter query – builds WHERE clause from non-null parameters.
     *
     * DBMS concept: parameterised dynamic SQL.  Every value is a bind variable
     * (? placeholder) – the strings are never interpolated into the SQL text.
     *
     * Filters: name (LIKE, matches first or last), programId, departmentId, status.
     * Pagination: LIMIT ? OFFSET ? appended last.
     */
    public List<StudentDto> search(String name, Integer programId, Integer departmentId,
                                   String status, PageRequest page) {
        StringBuilder sql = new StringBuilder(BASE_SELECT +
                " JOIN department d ON pr.department_id = d.department_id WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (name != null && !name.isBlank()) {
            sql.append(" AND (p.first_name LIKE ? OR p.last_name LIKE ?)");
            String like = "%" + name + "%";
            params.add(like); params.add(like);
        }
        if (programId != null) {
            sql.append(" AND s.program_id = ?");
            params.add(programId);
        }
        if (departmentId != null) {
            sql.append(" AND d.department_id = ?");
            params.add(departmentId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND s.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY p.last_name, p.first_name LIMIT ? OFFSET ?");
        params.add(page.getSize());
        params.add(page.offset());

        return jdbc.queryTraced(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public void update(int id, UpdateStudentRequest r) {
        jdbc.updateTraced(UPDATE_PERSON,
                r.getAddressLine(), r.getCity(), r.getState(), r.getPinCode(), id);
        jdbc.updateTraced(UPDATE_STUDENT,
                r.getStatus(), r.getCurrentSemester(), id);
    }

    /**
     * Hard delete: blocked at DB level by trg_student_before_delete if the
     * student has any enrollment or fee_payment rows.  That trigger fires a
     * SIGNAL 45000 which GlobalExceptionHandler maps to HTTP 409.
     * Sequence: delete student child row first, then person parent row.
     */
    public void delete(int id) {
        jdbc.updateTraced(DELETE_STUDENT, id);
        jdbc.updateTraced(DELETE_PERSON,  id);
    }
}
