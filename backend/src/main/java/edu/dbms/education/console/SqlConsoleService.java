package edu.dbms.education.console;

import edu.dbms.education.report.ReportRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Read-only SQL console restricted to SELECT statements.
 *
 * Safety model (defence in depth):
 *
 * 1. @PreAuthorize("hasRole('ADMIN')") on the controller – no other role
 *    can reach this service at all.
 *
 * 2. Statement-type validator (this class) – rejects anything that does not
 *    begin with SELECT (after stripping comments and whitespace).  This is
 *    a belt-and-suspenders guard; it is NOT a security boundary on its own
 *    because regex-based SQL parsing is not complete.
 *
 * 3. Database-level enforcement – app_user has no DDL rights (no CREATE,
 *    ALTER, DROP, TRUNCATE).  Even if the validator were bypassed, the DB
 *    would reject a DDL statement.  DML (INSERT/UPDATE/DELETE) would still
 *    succeed at the DB level if the validator were bypassed, hence layers 1
 *    and 2 above are important.
 *
 * 4. Single-statement enforcement – we reject SQL that contains a semicolon
 *    followed by non-whitespace text, preventing "stacked queries" of the form
 *    "SELECT 1; DROP TABLE students".  The MySQL JDBC driver already rejects
 *    multi-statement queries unless allowMultiQueries=true (not set), but this
 *    provides a clear error message at the application layer.
 *
 * DBMS concept: parameterised bind values.  Even in the console, params are
 * ? bind values sent via PreparedStatement – the SQL text is never modified
 * or concatenated with the values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlConsoleService {

    private final ReportRunner runner;

    // Matches the leading keyword after stripping block/line comments
    private static final Pattern SELECT_PATTERN =
            Pattern.compile("^\\s*(SELECT|WITH)\\b", Pattern.CASE_INSENSITIVE);

    // Stacked-query guard: semicolon followed by non-whitespace
    private static final Pattern MULTI_STMT_PATTERN =
            Pattern.compile(";\\s*\\S", Pattern.DOTALL);

    // MySQL block comment pattern used to strip them before keyword check
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT  = Pattern.compile("--[^\r\n]*");

    /**
     * Validates and executes a single SELECT statement.
     *
     * @throws IllegalArgumentException if the statement is not SELECT or
     *                                   contains multiple statements
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> execute(String sql, List<Object> params) {
        validate(sql);
        Object[] bindValues = params == null ? new Object[0] : params.toArray();
        log.info("SqlConsole: ADMIN executing SELECT (params={})", bindValues.length);
        return runner.run(sql, bindValues);
    }

    // ── Validation ────────────────────────────────────────────────────────

    private void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL statement cannot be empty.");
        }

        // Strip comments before keyword check to prevent bypass via:
        //   /* evil */ INSERT INTO ...
        String stripped = BLOCK_COMMENT.matcher(sql).replaceAll(" ");
        stripped        = LINE_COMMENT.matcher(stripped).replaceAll(" ");

        // Rule 1: must begin with SELECT or WITH (for CTEs like q16–q18)
        if (!SELECT_PATTERN.matcher(stripped).find()) {
            throw new IllegalArgumentException(
                    "Console only accepts SELECT statements. " +
                    "The statement must begin with SELECT or WITH.");
        }

        // Rule 2: no stacked statements (semicolon followed by non-whitespace)
        if (MULTI_STMT_PATTERN.matcher(stripped).find()) {
            throw new IllegalArgumentException(
                    "Console does not allow multiple statements. " +
                    "Remove any semicolons that are followed by SQL text.");
        }
    }
}
