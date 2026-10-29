package edu.dbms.education.faculty;

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

@Repository
@RequiredArgsConstructor
public class FacultyRepository {

    private final TracingJdbcTemplate jdbc;

    // ── SQL ──────────────────────────────────────────────────────────────

    private static final String INSERT_PERSON =
            "INSERT INTO person (first_name, last_name, email, date_of_birth, gender)" +
            " VALUES (?,?,?,?,?)";

    private static final String INSERT_FACULTY =
            "INSERT INTO faculty (faculty_id, employee_no, department_id, designation, hire_date, max_load)" +
            " VALUES (?,?,?,?,?,?)";

    private static final String BASE_SELECT =
            "SELECT p.person_id, p.first_name, p.last_name, p.email," +
            "  f.employee_no, f.department_id, d.name AS department_name," +
            "  f.designation, f.hire_date, f.max_load" +
            " FROM faculty f" +
            " JOIN person p     ON f.faculty_id    = p.person_id" +
            " JOIN department d ON f.department_id = d.department_id";

    private static final String SELECT_BY_ID = BASE_SELECT + " WHERE f.faculty_id = ?";

    private static final String UPDATE_FACULTY =
            "UPDATE faculty SET designation = ?, max_load = ?, department_id = ? WHERE faculty_id = ?";

    private static final String DELETE_FACULTY = "DELETE FROM faculty WHERE faculty_id = ?";
    private static final String DELETE_PERSON  = "DELETE FROM person  WHERE person_id  = ?";

    // ── RowMapper ─────────────────────────────────────────────────────────

    static final RowMapper<FacultyDto> ROW_MAPPER = (rs, n) -> new FacultyDto(
            rs.getInt("person_id"),
            rs.getString("first_name"),
            rs.getString("last_name"),
            rs.getString("email"),
            rs.getString("employee_no"),
            rs.getInt("department_id"),
            rs.getString("department_name"),
            rs.getString("designation"),
            rs.getDate("hire_date").toLocalDate(),
            (Integer) rs.getObject("max_load")
    );

    // ── CRUD ─────────────────────────────────────────────────────────────

    public int create(CreateFacultyRequest r) {
        KeyHolder kh = new GeneratedKeyHolder();
        long start = System.currentTimeMillis();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT_PERSON, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, r.getFirstName());
            ps.setString(2, r.getLastName());
            ps.setString(3, r.getEmail());
            ps.setDate(4, Date.valueOf(r.getDateOfBirth()));
            ps.setString(5, r.getGender());
            return ps;
        }, kh);
        jdbc.getSqlTraceService().record(INSERT_PERSON,
                List.of(r.getFirstName(), r.getLastName(), r.getEmail()), 1,
                System.currentTimeMillis() - start);

        int personId = kh.getKey().intValue();
        jdbc.updateTraced(INSERT_FACULTY,
                personId, r.getEmployeeNo(), r.getDepartmentId(),
                r.getDesignation(), Date.valueOf(r.getHireDate()), r.getMaxLoad());
        return personId;
    }

    public Optional<FacultyDto> findById(int id) {
        try {
            return Optional.of(jdbc.queryForObjectTraced(SELECT_BY_ID, ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<FacultyDto> search(String name, Integer departmentId, PageRequest page) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (name != null && !name.isBlank()) {
            sql.append(" AND (p.first_name LIKE ? OR p.last_name LIKE ?)");
            String like = "%" + name + "%";
            params.add(like); params.add(like);
        }
        if (departmentId != null) {
            sql.append(" AND f.department_id = ?");
            params.add(departmentId);
        }
        sql.append(" ORDER BY p.last_name, p.first_name LIMIT ? OFFSET ?");
        params.add(page.getSize()); params.add(page.offset());

        return jdbc.queryTraced(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public void update(int id, UpdateFacultyRequest r) {
        jdbc.updateTraced(UPDATE_FACULTY,
                r.getDesignation(), r.getMaxLoad(), r.getDepartmentId(), id);
    }

    public void delete(int id) {
        jdbc.updateTraced(DELETE_FACULTY, id);
        jdbc.updateTraced(DELETE_PERSON,  id);
    }
}
