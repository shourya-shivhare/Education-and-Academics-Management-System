package edu.dbms.education.auth;

import edu.dbms.education.trace.TracingJdbcTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Low-level data access for the {@code users} table.
 *
 * DBMS concept: parameterised queries.
 * Every SQL string uses {@code ?} placeholders – the username is never
 * concatenated into the SQL text. TracingJdbcTemplate sends the SQL template
 * and the bind value to MySQL as two separate protocol messages, making
 * SQL injection structurally impossible at the wire level.
 *
 * The full SQL text and the bound parameter value both appear in the
 * SqlTraceService trace that is returned with every API response.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final TracingJdbcTemplate jdbc;

    // ── SQL ──────────────────────────────────────────────────────────────

    /*
     * SELECT by username.
     * role is a reserved word in MySQL 8 so it is backtick-quoted.
     * We also pull person_id (nullable) so the JWT can embed it.
     */
    private static final String SQL_FIND_BY_USERNAME =
            "SELECT user_id, person_id, username, password_hash, `role`, is_active " +
            "FROM users " +
            "WHERE username = ?";

    private static final String SQL_UPDATE_LAST_LOGIN =
            "UPDATE users SET last_login = NOW() WHERE user_id = ?";

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Looks up a user by username.
     *
     * Uses {@link TracingJdbcTemplate#queryForObjectTraced} so the query
     * appears in the SqlTrace attached to the /api/auth/login response.
     *
     * @param username  exact, case-sensitive username (UNIQUE index used)
     * @return          Optional.empty() if no row found
     */
    public Optional<AppUser> findByUsername(String username) {
        try {
            AppUser user = jdbc.queryForObjectTraced(
                    SQL_FIND_BY_USERNAME,
                    ROW_MAPPER,
                    username   // bound as ? – never concatenated
            );
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            // No row found – return empty; the service converts to 401
            return Optional.empty();
        }
    }

    /**
     * Records the time of a successful login.
     * Stamped AFTER authentication succeeds, not before.
     *
     * DBMS concept: DML through a parameterised UPDATE – user_id is a bind
     * value, not part of the SQL string.
     */
    public void stampLastLogin(int userId) {
        jdbc.updateTraced(SQL_UPDATE_LAST_LOGIN, userId);
    }

    // ── RowMapper ─────────────────────────────────────────────────────────

    private static final RowMapper<AppUser> ROW_MAPPER = (rs, rowNum) -> new AppUser(
            rs.getInt("user_id"),
            rs.getObject("person_id", Integer.class), // nullable
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("role"),
            rs.getBoolean("is_active")
    );
}
