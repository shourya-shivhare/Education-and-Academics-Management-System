package edu.dbms.education.schema;

import edu.dbms.education.api.ResponseEnvelope;
import edu.dbms.education.trace.TracingJdbcTemplate;
import edu.dbms.education.trace.SqlTraceService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Schema introspection endpoints.
 *
 * DBMS concept: information_schema is a set of system views defined by the
 * SQL standard (ISO/IEC 9075).  MySQL 8 implements information_schema as
 * in-memory tables populated from the data dictionary.  Querying it is a
 * standard, non-proprietary way to introspect table structure, column
 * metadata, and referential integrity constraints.
 *
 * All queries run as app_user against information_schema; the database name
 * is injected from the datasource URL property so the schema name is not
 * hard-coded in the SQL text.
 *
 * ┌──────────────────────────────────────────┬─────────────────────┐
 * │ Endpoint                                 │ Access              │
 * ├──────────────────────────────────────────┼─────────────────────┤
 * │ GET /api/schema                          │ ADMIN               │
 * │ GET /api/schema?table=<name>             │ ADMIN               │
 * │ GET /api/schema/er-image                 │ ADMIN               │
 * └──────────────────────────────────────────┴─────────────────────┘
 */
@Slf4j
@RestController
@RequestMapping("/api/schema")
@RequiredArgsConstructor
public class SchemaController {

    private final TracingJdbcTemplate jdbc;
    private final SqlTraceService     trace;

    /**
     * Database name read from the datasource URL at startup.
     * Injected so it never needs to be hard-coded in the SQL below.
     */
    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    // ── Schema introspection ──────────────────────────────────────────────

    /**
     * Returns tables, columns, primary keys, unique keys, and foreign keys
     * for the edu_db database (or a single table when ?table= is provided).
     *
     * Three queries run in sequence; all appear in the trace.
     *
     * DBMS concept: information_schema.COLUMNS, KEY_COLUMN_USAGE, and
     * REFERENTIAL_CONSTRAINTS together describe the full logical schema.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseEnvelope<Map<String, Object>>> schema(
            @RequestParam(required = false) String table) {

        String dbName = extractDbName(datasourceUrl);

        // 1. Tables with row-count estimates (from information_schema.TABLES)
        String tablesSql =
                "SELECT TABLE_NAME, TABLE_TYPE, ENGINE, TABLE_ROWS," +
                "  DATA_LENGTH, CREATE_TIME, TABLE_COMMENT" +
                " FROM information_schema.TABLES" +
                " WHERE TABLE_SCHEMA = ?" +
                (table != null ? " AND TABLE_NAME = ?" : "") +
                " ORDER BY TABLE_NAME";
        List<Map<String, Object>> tables = table != null
                ? jdbc.queryTraced(tablesSql, new ColumnMapRowMapper(), dbName, table)
                : jdbc.queryTraced(tablesSql, new ColumnMapRowMapper(), dbName);

        // 2. Columns with types, nullability, defaults, and extra info
        String columnsSql =
                "SELECT TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME," +
                "  COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT," +
                "  COLUMN_KEY, EXTRA, COLUMN_COMMENT" +
                " FROM information_schema.COLUMNS" +
                " WHERE TABLE_SCHEMA = ?" +
                (table != null ? " AND TABLE_NAME = ?" : "") +
                " ORDER BY TABLE_NAME, ORDINAL_POSITION";
        List<Map<String, Object>> columns = table != null
                ? jdbc.queryTraced(columnsSql, new ColumnMapRowMapper(), dbName, table)
                : jdbc.queryTraced(columnsSql, new ColumnMapRowMapper(), dbName);

        // 3. Foreign-key relationships (child table → parent table)
        //    information_schema.KEY_COLUMN_USAGE + REFERENTIAL_CONSTRAINTS joined
        String fkSql =
                "SELECT kcu.TABLE_NAME AS child_table," +
                "  kcu.COLUMN_NAME AS child_column," +
                "  kcu.CONSTRAINT_NAME," +
                "  kcu.REFERENCED_TABLE_NAME AS parent_table," +
                "  kcu.REFERENCED_COLUMN_NAME AS parent_column," +
                "  rc.UPDATE_RULE, rc.DELETE_RULE" +
                " FROM information_schema.KEY_COLUMN_USAGE kcu" +
                " JOIN information_schema.REFERENTIAL_CONSTRAINTS rc" +
                "   ON rc.CONSTRAINT_SCHEMA = kcu.TABLE_SCHEMA" +
                "   AND rc.CONSTRAINT_NAME  = kcu.CONSTRAINT_NAME" +
                " WHERE kcu.TABLE_SCHEMA = ?" +
                "   AND kcu.REFERENCED_TABLE_NAME IS NOT NULL" +
                (table != null ? " AND (kcu.TABLE_NAME = ? OR kcu.REFERENCED_TABLE_NAME = ?)" : "") +
                " ORDER BY kcu.TABLE_NAME, kcu.CONSTRAINT_NAME";
        List<Map<String, Object>> foreignKeys = table != null
                ? jdbc.queryTraced(fkSql, new ColumnMapRowMapper(), dbName, table, table)
                : jdbc.queryTraced(fkSql, new ColumnMapRowMapper(), dbName);

        // 4. Indexes (covering, composite, fulltext)
        String indexSql =
                "SELECT TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME," +
                "  NON_UNIQUE, INDEX_TYPE, COMMENT" +
                " FROM information_schema.STATISTICS" +
                " WHERE TABLE_SCHEMA = ?" +
                (table != null ? " AND TABLE_NAME = ?" : "") +
                " ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX";
        List<Map<String, Object>> indexes = table != null
                ? jdbc.queryTraced(indexSql, new ColumnMapRowMapper(), dbName, table)
                : jdbc.queryTraced(indexSql, new ColumnMapRowMapper(), dbName);

        Map<String, Object> payload = Map.of(
                "database",   dbName,
                "tables",     tables,
                "columns",    columns,
                "foreignKeys", foreignKeys,
                "indexes",    indexes
        );

        return ResponseEntity.ok(ResponseEnvelope.ok(payload, trace.getEntries()));
    }

    // ── ER diagram image ──────────────────────────────────────────────────

    /**
     * Serves docs/er_diagram.png as a binary image response.
     *
     * The path is resolved relative to the project root (two levels up from
     * the backend/ directory, then into docs/).  If the file doesn't exist
     * a 404 with a descriptive message is returned.
     *
     * To generate the PNG, use MySQL Workbench → File → Export → Export as PNG,
     * or any ER-diagram tool, and save the result as docs/er_diagram.png.
     */
    @GetMapping("/er-image")
    @PreAuthorize("hasRole('ADMIN')")
    public void erImage(HttpServletResponse response) throws IOException {
        // Resolve relative to the working directory (project root when run via Maven)
        Path imagePath = Paths.get("docs", "er_diagram.png").toAbsolutePath();

        // Also try relative to the JAR location (for Docker/production deployments)
        if (!Files.exists(imagePath)) {
            // Try one level up from backend/ if running from the backend directory
            imagePath = Paths.get("..", "docs", "er_diagram.png").toAbsolutePath();
        }

        if (!Files.exists(imagePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "ER diagram not found. Generate docs/er_diagram.png from your DB tool " +
                    "and place it at: " + Paths.get("docs", "er_diagram.png").toAbsolutePath());
            return;
        }

        response.setContentType(MediaType.IMAGE_PNG_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"er_diagram.png\"");
        response.setContentLengthLong(Files.size(imagePath));

        try (InputStream in = Files.newInputStream(imagePath)) {
            in.transferTo(response.getOutputStream());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Extracts the database name from a JDBC URL.
     * jdbc:mysql://host:port/DB_NAME?params  →  "DB_NAME"
     */
    static String extractDbName(String jdbcUrl) {
        try {
            // Strip jdbc:mysql:// prefix and query params
            String path = jdbcUrl.replaceFirst("jdbc:mysql://[^/]+/", "");
            int q = path.indexOf('?');
            return q >= 0 ? path.substring(0, q) : path;
        } catch (Exception e) {
            return "edu_db";  // safe fallback
        }
    }
}
