package edu.dbms.education.enrollment;

import edu.dbms.education.api.ResponseEnvelope;
import edu.dbms.education.auth.AuthenticatedUser;
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
 *   ADMIN / REGISTRAR – enroll, drop, search all
 *   STUDENT           – GET own enrollments only (studentId = JWT personId)
 *   FACULTY           – GET enrollments for their own offerings (via offeringId filter)
 */
@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService service;
    private final SqlTraceService   trace;

    // ── Enroll (CALL sp_enroll_student) ───────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR')")
    public ResponseEntity<ResponseEnvelope<Void>> enroll(
            @Valid @RequestBody EnrollRequest req) {
        service.enroll(req.getStudentId(), req.getOfferingId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseEnvelope.ok(null, trace.getEntries()));
    }

    // ── Drop (CALL sp_drop_enrollment) ────────────────────────────────────

    @DeleteMapping
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR')")
    public ResponseEntity<ResponseEnvelope<Void>> drop(
            @Valid @RequestBody EnrollRequest req) {
        service.drop(req.getStudentId(), req.getOfferingId());
        return ResponseEntity.ok(ResponseEnvelope.ok(null, trace.getEntries()));
    }

    // ── Read ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR','FACULTY') or hasRole('STUDENT')")
    public ResponseEntity<ResponseEnvelope<EnrollmentDto>> getById(@PathVariable int id) {
        return ResponseEntity.ok(ResponseEnvelope.ok(service.getById(id), trace.getEntries()));
    }

    /**
     * STUDENT: always filtered to own records (studentId injected from JWT).
     * FACULTY: can filter by offeringId to see who is in their offerings.
     * ADMIN/REGISTRAR: unrestricted.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseEnvelope<List<EnrollmentDto>>> search(
            @RequestParam(required = false) Integer studentId,
            @RequestParam(required = false) Integer offeringId,
            @RequestParam(required = false) Integer termId,
            @RequestParam(required = false) String  status,
            @Valid PageRequest page) {

        AuthenticatedUser u = AuthenticatedUser.current();
        Integer effectiveStudentId = studentId;

        if ("Student".equals(u.getRole())) {
            // STUDENT can only see their own enrollments regardless of query param
            effectiveStudentId = u.getPersonId();
        }

        return ResponseEntity.ok(ResponseEnvelope.ok(
                service.search(effectiveStudentId, offeringId, termId, status, page),
                trace.getEntries()));
    }
}
