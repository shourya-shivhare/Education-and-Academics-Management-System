package edu.dbms.education.subject;

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
 *   ADMIN              – full CRUD
 *   REGISTRAR/FACULTY/STUDENT – read only
 */
@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService  service;
    private final SqlTraceService trace;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseEnvelope<SubjectDto>> create(
            @Valid @RequestBody CreateSubjectRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseEnvelope.ok(service.create(req), trace.getEntries()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseEnvelope<SubjectDto>> getById(@PathVariable int id) {
        return ResponseEntity.ok(ResponseEnvelope.ok(service.getById(id), trace.getEntries()));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseEnvelope<List<SubjectDto>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) Integer programId,
            @RequestParam(required = false) Integer semester,
            @Valid PageRequest page) {
        return ResponseEntity.ok(ResponseEnvelope.ok(
                service.search(keyword, departmentId, programId, semester, page),
                trace.getEntries()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseEnvelope<SubjectDto>> update(
            @PathVariable int id,
            @Valid @RequestBody CreateSubjectRequest req) {
        return ResponseEntity.ok(ResponseEnvelope.ok(service.update(id, req), trace.getEntries()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseEnvelope<Void>> delete(@PathVariable int id) {
        service.delete(id);
        return ResponseEntity.ok(ResponseEnvelope.ok(null, trace.getEntries()));
    }
}
