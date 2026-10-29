package edu.dbms.education.marks;

import edu.dbms.education.api.ResponseEnvelope;
import edu.dbms.education.common.PageRequest;
import edu.dbms.education.trace.SqlTraceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Role rules:
 *   ADMIN/REGISTRAR – read only
 *   FACULTY         – INSERT + UPDATE marks for their own offerings
 *   STUDENT         – read own marks only
 *
 * Optimistic lock: the PUT body must include the current `version`.
 * If that version no longer matches the DB row, HTTP 409 is returned.
 */
@RestController
@RequestMapping("/api/marks")
@RequiredArgsConstructor
public class MarksController {

    private final MarksService    service;
    private final SqlTraceService trace;

    /**
     * Upsert: INSERT when version is null, optimistic UPDATE when version is supplied.
     * FACULTY only (plus ADMIN for corrections).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY')")
    public ResponseEntity<ResponseEnvelope<MarksDto>> upsert(
            @Valid @RequestBody UpsertMarksRequest req) {
        MarksDto dto = service.upsert(req);
        HttpStatus status = req.getVersion() == null ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(ResponseEnvelope.ok(dto, trace.getEntries()));
    }

    /**
     * GET by composite PK.
     * STUDENT can read their own marks (studentId check in service).
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseEnvelope<List<MarksDto>>> search(
            @RequestParam(required = false) Integer offeringId,
            @RequestParam(required = false) Integer enrollmentId,
            @RequestParam(required = false) Integer studentId,
            @Valid PageRequest page) {
        return ResponseEntity.ok(ResponseEnvelope.ok(
                service.search(offeringId, enrollmentId, studentId, page),
                trace.getEntries()));
    }

    @GetMapping("/entry")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseEnvelope<MarksDto>> getByPk(
            @RequestParam int enrollmentId,
            @RequestParam int offeringId,
            @RequestParam int assessmentNo) {
        return ResponseEntity.ok(ResponseEnvelope.ok(
                service.getByPk(enrollmentId, offeringId, assessmentNo),
                trace.getEntries()));
    }
}
