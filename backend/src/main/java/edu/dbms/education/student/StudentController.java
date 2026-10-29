package edu.dbms.education.student;

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
 *   ADMIN / REGISTRAR – full CRUD
 *   STUDENT            – GET own record only (student_id = JWT personId)
 *   FACULTY            – no access (403 on all methods)
 */
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService  service;
    private final SqlTraceService trace;

    // ── Create ───────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR')")
    public ResponseEntity<ResponseEnvelope<StudentDto>> create(
            @Valid @RequestBody CreateStudentRequest req) {
        StudentDto dto = service.create(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseEnvelope.ok(dto, trace.getEntries()));
    }

    // ── Read ─────────────────────────────────────────────────────────────

    /**
     * STUDENT can only GET their own record.
     * ADMIN/REGISTRAR can GET any record.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR') or " +
                  "(hasRole('STUDENT') and #id == authentication.principal.personId)")
    public ResponseEntity<ResponseEnvelope<StudentDto>> getById(@PathVariable int id) {
        return ResponseEntity.ok(ResponseEnvelope.ok(service.getById(id), trace.getEntries()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR')")
    public ResponseEntity<ResponseEnvelope<List<StudentDto>>> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer programId,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) String status,
            @Valid PageRequest page) {
        return ResponseEntity.ok(
                ResponseEnvelope.ok(service.search(name, programId, departmentId, status, page),
                        trace.getEntries()));
    }

    /**
     * Student GET /api/students/me – returns their own record using personId from JWT.
     * No id path variable needed – scoped to the authenticated student.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ResponseEnvelope<StudentDto>> me() {
        int studentId = AuthenticatedUser.currentPersonId();
        return ResponseEntity.ok(ResponseEnvelope.ok(service.getById(studentId), trace.getEntries()));
    }

    // ── Update ───────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR')")
    public ResponseEntity<ResponseEnvelope<StudentDto>> update(
            @PathVariable int id,
            @Valid @RequestBody UpdateStudentRequest req) {
        return ResponseEntity.ok(ResponseEnvelope.ok(service.update(id, req), trace.getEntries()));
    }

    // ── Delete ───────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseEnvelope<Void>> delete(@PathVariable int id) {
        service.delete(id);
        return ResponseEntity.ok(ResponseEnvelope.ok(null, trace.getEntries()));
    }
}
