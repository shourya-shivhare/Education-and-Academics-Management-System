package edu.dbms.education.offering;

import edu.dbms.education.api.ResponseEnvelope;
import edu.dbms.education.auth.AuthenticatedUser;
import edu.dbms.education.common.PageRequest;
import edu.dbms.education.trace.SqlTraceService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Role rules:
 *   ADMIN/REGISTRAR – full CRUD + status changes
 *   FACULTY – GET their own offerings (facultyId filter applied automatically)
 *   STUDENT – GET open offerings (read only)
 */
@RestController
@RequestMapping("/api/offerings")
@RequiredArgsConstructor
public class CourseOfferingController {

    private final CourseOfferingService service;
    private final SqlTraceService       trace;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR')")
    public ResponseEntity<ResponseEnvelope<CourseOfferingDto>> create(
            @Valid @RequestBody CreateOfferingRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseEnvelope.ok(service.create(req), trace.getEntries()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseEnvelope<CourseOfferingDto>> getById(@PathVariable int id) {
        return ResponseEntity.ok(ResponseEnvelope.ok(service.getById(id), trace.getEntries()));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseEnvelope<List<CourseOfferingDto>>> search(
            @RequestParam(required = false) Integer subjectId,
            @RequestParam(required = false) Integer termId,
            @RequestParam(required = false) String  status,
            @RequestParam(required = false) Integer departmentId,
            @Valid PageRequest page) {

        // FACULTY: automatically restrict to their own offerings
        Integer facultyId = null;
        AuthenticatedUser u = AuthenticatedUser.current();
        if ("Faculty".equals(u.getRole())) {
            facultyId = u.getPersonId();
        }

        return ResponseEntity.ok(ResponseEnvelope.ok(
                service.search(subjectId, facultyId, termId, status, departmentId, page),
                trace.getEntries()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR')")
    public ResponseEntity<ResponseEnvelope<CourseOfferingDto>> updateStatus(
            @PathVariable int id,
            @RequestBody StatusRequest body) {
        return ResponseEntity.ok(ResponseEnvelope.ok(
                service.updateStatus(id, body.getStatus()), trace.getEntries()));
    }

    @PatchMapping("/{id}/capacity")
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR')")
    public ResponseEntity<ResponseEnvelope<CourseOfferingDto>> updateCapacity(
            @PathVariable int id,
            @RequestBody CapacityRequest body) {
        return ResponseEntity.ok(ResponseEnvelope.ok(
                service.updateCapacity(id, body.getCapacity()), trace.getEntries()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseEnvelope<Void>> delete(@PathVariable int id) {
        service.delete(id);
        return ResponseEntity.ok(ResponseEnvelope.ok(null, trace.getEntries()));
    }

    // ── Inline request bodies ─────────────────────────────────────────────

    @Getter @Setter
    static class StatusRequest   { private String status; }

    @Getter @Setter
    static class CapacityRequest { private int capacity;  }
}
