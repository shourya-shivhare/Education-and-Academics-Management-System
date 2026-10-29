package edu.dbms.education.subject;

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
public class SubjectRepository {

    private final TracingJdbcTemplate jdbc;

    // ── SQL ──────────────────────────────────────────────────────────────

    private static final String INSERT =
            "INSERT INTO subject (code, title, credits, department_id, program_id, semester, description)" +
            " VALUES (?,?,?,?,?,?,?)";

    private static final String BASE_SELECT =
            "SELECT s.subject_id, s.code, s.title, s.credits," +
            "  s.department_id, d.name AS department_name," +
            "  s.program_id, s.semester, s.description" +
            " FROM subject s" +
            " JOIN department d ON s.department_id = d.department_id";

    private static final String SELECT_BY_ID  = BASE_SELECT + " WHERE s.subject_id = ?";

    private static final String UPDATE =
            "UPDATE subject SET title = ?, credits = ?, description = ?," +
            " department_id = ?, program_id = ?, semester = ? WHERE subject_id = ?";

    private static final String DELETE = "DELETE FROM subject WHERE subject_id = ?";

    // ── RowMapper ─────────────────────────────────────────────────────────

    static final RowMapper<SubjectDto> ROW_MAPPER = (rs, n) -> new SubjectDto(
            rs.getInt("subject_id"),
            rs.getString("code"),
            rs.getString("title"),
            rs.getInt("credits"),
            rs.getInt("department_id"),
            rs.getString("department_name"),
            (Integer) rs.getObject("program_id"),
            rs.getInt("semester"),
            rs.getString("description")
    );

    // ── CRUD ─────────────────────────────────────────────────────────────

    public int create(CreateSubjectRequest r) {
        KeyHolder kh = new GeneratedKeyHolder();
        long t = System.currentTimeMillis();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, r.getCode());
            ps.setString(2, r.getTitle());
            ps.setInt(3, r.getCredits());
            ps.setInt(4, r.getDepartmentId());
            ps.setObject(5, r.getProgramId());
            ps.setInt(6, r.getSemester());
            ps.setString(7, r.getDescription());
            return ps;
        }, kh);
        jdbc.getSqlTraceService().record(INSERT, List.of(r.getCode(), r.getTitle()), 1,
                System.currentTimeMillis() - t);
        return kh.getKey().intValue();
    }

    public Optional<SubjectDto> findById(int id) {
        try {
            return Optional.of(jdbc.queryForObjectTraced(SELECT_BY_ID, ROW_MAPPER, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Supports FULLTEXT search when a keyword is provided.
     * MATCH(title, description) AGAINST(? IN BOOLEAN MODE) uses the
     * ft_subject FULLTEXT index created in V4__indexes.sql.
     * Falls back to LIKE when no keyword given.
     */
    public List<SubjectDto> search(String keyword, Integer departmentId,
                                   Integer programId, Integer semester, PageRequest page) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            // FULLTEXT boolean mode: prefix match e.g. "data*"
            sql.append(" AND MATCH(s.title, s.description) AGAINST(? IN BOOLEAN MODE)");
            params.add(keyword);
        }
        if (departmentId != null) {
            sql.append(" AND s.department_id = ?");
            params.add(departmentId);
        }
        if (programId != null) {
            sql.append(" AND s.program_id = ?");
            params.add(programId);
        }
        if (semester != null) {
            sql.append(" AND s.semester = ?");
            params.add(semester);
        }
        sql.append(" ORDER BY s.code LIMIT ? OFFSET ?");
        params.add(page.getSize()); params.add(page.offset());

        return jdbc.queryTraced(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public void update(int id, CreateSubjectRequest r) {
        jdbc.updateTraced(UPDATE,
                r.getTitle(), r.getCredits(), r.getDescription(),
                r.getDepartmentId(), r.getProgramId(), r.getSemester(), id);
    }

    public void delete(int id) {
        jdbc.updateTraced(DELETE, id);
    }
}
