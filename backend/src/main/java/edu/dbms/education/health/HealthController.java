package edu.dbms.education.health;

import edu.dbms.education.api.ResponseEnvelope;
import edu.dbms.education.trace.SqlTraceService;
import edu.dbms.education.trace.TracingJdbcTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GET /api/health
 *
 * Minimal liveness check that exercises the full request pipeline:
 *   1. An HTTP request arrives.
 *   2. TracingJdbcTemplate executes "SELECT 1" against MySQL via app_user.
 *   3. The SQL trace entry is captured in SqlTraceService (request-scoped).
 *   4. The response is wrapped in ResponseEnvelope{ data, trace }.
 *
 * DBMS concept demonstrated: even a trivial "SELECT 1" is a parameterised
 * statement going through the prepared-statement path; the trace shows its
 * execution time in ms, confirming round-trip database connectivity.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final TracingJdbcTemplate jdbcTemplate;
    private final SqlTraceService     traceService;

    @GetMapping("/health")
    public ResponseEntity<ResponseEnvelope<Map<String, Object>>> health() {

        // SELECT 1 – lightest possible connectivity probe
        // queryForScalarTraced records the SQL + timing in SqlTraceService
        Integer ping = jdbcTemplate.queryForScalarTraced(
                "SELECT 1",
                Integer.class
        );

        Map<String, Object> data = Map.of(
                "status",   "UP",
                "database", "edu_db",
                "ping",     ping
        );

        return ResponseEntity.ok(
                ResponseEnvelope.ok(data, traceService.getEntries())
        );
    }
}
