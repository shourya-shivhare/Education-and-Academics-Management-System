package edu.dbms.education.report;

import edu.dbms.education.api.ResponseEnvelope;
import edu.dbms.education.trace.SqlTraceService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

/**
 * Report endpoints.
 *
 * ┌────────────────────────────────────────────────────────┬──────────────┐
 * │ Endpoint                                               │ Access       │
 * ├────────────────────────────────────────────────────────┼──────────────┤
 * │ GET  /api/reports                                      │ All authed   │
 * │ POST /api/reports/{id}/run                             │ All authed   │
 * │ POST /api/reports/{id}/run?format=csv                  │ All authed   │
 * │ GET  /api/reports/transcript/{studentId}               │ ADMIN/REG/own│
 * │ GET  /api/reports/attendance-shortage                  │ ADMIN/REG/FAC│
 * │ GET  /api/reports/toppers                              │ All authed   │
 * │ GET  /api/reports/department-performance               │ All authed   │
 * │ GET  /api/reports/transcript/{id}?format=csv           │ (same)       │
 * └────────────────────────────────────────────────────────┴──────────────┘
 *
 * CSV export: add ?format=csv to any run endpoint.
 * The response Content-Type becomes text/csv and Content-Disposition triggers
 * a browser download.  The trace is NOT included in CSV mode (no JSON wrapper).
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService   reportService;
    private final CsvExporter     csvExporter;
    private final SqlTraceService trace;

    // ── Catalogue ─────────────────────────────────────────────────────────

    /**
     * Lists all 22 query definitions (id, title, concept, description, params).
     * The SQL text is intentionally included so the frontend can show it.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseEnvelope<List<QueryDefinition>>> list() {
        return ResponseEntity.ok(
                ResponseEnvelope.ok(reportService.listAll(), trace.getEntries()));
    }

    // ── Named-query runner ────────────────────────────────────────────────

    /**
     * Executes one named query (q01–q22) with positional parameters.
     *
     * DBMS concept: every ? in the SQL template is a prepared-statement
     * bind placeholder. The params array travels separately from the SQL
     * string – structural SQL-injection prevention at the protocol level.
     *
     * ?format=csv streams the result as a downloadable CSV file.
     * The JSON envelope is omitted in CSV mode; the trace is lost (acceptable –
     * CSV is a data export format, not a debugging tool).
     */
    @PostMapping("/{id}/run")
    @PreAuthorize("isAuthenticated()")
    public void run(@PathVariable String id,
                    @RequestBody(required = false) RunRequest body,
                    @RequestParam(defaultValue = "json") String format,
                    HttpServletResponse response) throws IOException {

        List<Object> params = body != null ? body.getParams() : null;
        List<Map<String, Object>> rows = reportService.run(id, params);

        if ("csv".equalsIgnoreCase(format)) {
            // Find the query title for the filename
            String title = reportService.listAll().stream()
                    .filter(q -> q.getId().equals(id))
                    .map(QueryDefinition::getTitle)
                    .findFirst()
                    .orElse(id);
            streamCsv(response, rows, id + "_" + slugify(title));
        } else {
            // Write JSON envelope manually to avoid return-type conflict
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    jsonEnvelope(rows));
        }
    }

    // ── Dedicated reports ─────────────────────────────────────────────────

    /**
     * Student academic transcript (reads v_student_transcript view).
     * STUDENT can only GET their own transcript (personId == studentId).
     */
    @GetMapping("/transcript/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR') or " +
                  "(hasRole('STUDENT') and #studentId == authentication.principal.personId)")
    public void transcript(@PathVariable int studentId,
                           @RequestParam(defaultValue = "json") String format,
                           HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = reportService.transcript(studentId);
        if ("csv".equalsIgnoreCase(format)) {
            streamCsv(response, rows, "transcript_student_" + studentId);
        } else {
            writeJson(response, rows);
        }
    }

    /**
     * Attendance shortage list – students below 75% in any offering.
     * Optionally filtered by termId query param.
     */
    @GetMapping("/attendance-shortage")
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR','FACULTY')")
    public void attendanceShortage(
            @RequestParam(required = false) Integer termId,
            @RequestParam(defaultValue = "json") String format,
            HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = reportService.attendanceShortage(termId);
        if ("csv".equalsIgnoreCase(format)) {
            streamCsv(response, rows, "attendance_shortage");
        } else {
            writeJson(response, rows);
        }
    }

    /**
     * Department toppers.  ?topN=N returns top-N students per department
     * (default 3).
     */
    @GetMapping("/toppers")
    @PreAuthorize("isAuthenticated()")
    public void toppers(@RequestParam(defaultValue = "3") int topN,
                        @RequestParam(defaultValue = "json") String format,
                        HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = reportService.toppers(topN);
        if ("csv".equalsIgnoreCase(format)) {
            streamCsv(response, rows, "toppers_top" + topN);
        } else {
            writeJson(response, rows);
        }
    }

    /**
     * Department academic performance (reads v_dept_performance view).
     * Optionally filtered by departmentId.
     */
    @GetMapping("/department-performance")
    @PreAuthorize("isAuthenticated()")
    public void departmentPerformance(
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(defaultValue = "json") String format,
            HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = reportService.departmentPerformance(departmentId);
        if ("csv".equalsIgnoreCase(format)) {
            streamCsv(response, rows, "dept_performance");
        } else {
            writeJson(response, rows);
        }
    }

    // ── CSV streaming helpers ─────────────────────────────────────────────

    /**
     * Writes rows as a CSV file to the response output stream.
     * Content-Disposition: attachment triggers a browser download.
     */
    private void streamCsv(HttpServletResponse response,
                            List<Map<String, Object>> rows,
                            String filenameBase) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filenameBase + ".csv\"");
        PrintWriter writer = response.getWriter();
        writer.write(csvExporter.toCsv(rows));
        writer.flush();
    }

    /**
     * Writes rows wrapped in the standard JSON envelope to the response.
     * Used when the controller method returns void (to share a signature
     * with the CSV branch via the same HttpServletResponse parameter).
     *
     * Note: the trace is included via the manually-built envelope string.
     * A simpler approach for the non-CSV branch is fine for our purposes.
     */
    private void writeJson(HttpServletResponse response,
                           List<Map<String, Object>> rows) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(jsonEnvelope(rows));
    }

    /**
     * Builds a minimal JSON envelope string for the rows result.
     * Uses Jackson indirectly via Spring's ObjectMapper would require
     * injection – instead we rely on the fact that ColumnMapRowMapper
     * returns Java primitives / strings / BigDecimal that serialise
     * cleanly with toString.
     *
     * For production use, inject ObjectMapper and call writeValueAsString.
     */
    private String jsonEnvelope(List<Map<String, Object>> rows) {
        // We need Jackson here; inject ObjectMapper in the constructor.
        // The field is added below – this is a forward reference.
        try {
            return objectMapper.writeValueAsString(
                    ResponseEnvelope.ok(rows, trace.getEntries()));
        } catch (Exception e) {
            return "{\"data\":null,\"trace\":[]}";
        }
    }

    // ObjectMapper is injected via constructor – declared here for clarity
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // Converts a report title to a safe filename slug
    private static String slugify(String title) {
        return title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+$", "");
    }
}
