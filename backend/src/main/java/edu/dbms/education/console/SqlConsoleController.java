package edu.dbms.education.console;

import edu.dbms.education.api.ErrorEnvelope;
import edu.dbms.education.api.ResponseEnvelope;
import edu.dbms.education.trace.SqlTraceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * POST /api/console/select
 *
 * Read-only interactive SQL console, restricted to ADMIN role.
 *
 * Intended use: ad-hoc diagnostic queries and DBMS concept exploration
 * during coursework demos. NOT for production use.
 *
 * The trace in the response shows the exact SQL template and bind values
 * sent to MySQL, demonstrating how PreparedStatement separates SQL from
 * data even for ad-hoc queries.
 *
 * Non-SELECT statements → HTTP 400 with code "CONSOLE_REJECTED".
 * Multi-statement SQL   → HTTP 400 with code "CONSOLE_REJECTED".
 */
@RestController
@RequestMapping("/api/console")
@RequiredArgsConstructor
public class SqlConsoleController {

    private final SqlConsoleService service;
    private final SqlTraceService   trace;

    /**
     * Executes a single parameterised SELECT statement.
     *
     * Request body:
     * {
     *   "sql":    "SELECT * FROM student WHERE program_id = ?",
     *   "params": [1]
     * }
     *
     * Response (success):
     * {
     *   "data":  [ { "student_id": 31, "roll_no": "23CS001", ... }, ... ],
     *   "trace": [ { "sql": "...", "params": ["1"], "rowsAffected": 5, "timeMs": 3 } ]
     * }
     */
    @PostMapping("/select")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> select(@Valid @RequestBody ConsoleRequest req) {
        try {
            List<Map<String, Object>> rows = service.execute(req.getSql(), req.getParams());
            return ResponseEntity.ok(ResponseEnvelope.ok(rows, trace.getEntries()));

        } catch (IllegalArgumentException ex) {
            // Validation failure from SqlConsoleService (non-SELECT / multi-stmt)
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorEnvelope("CONSOLE_REJECTED", ex.getMessage(), trace.getEntries()));
        }
    }
}
