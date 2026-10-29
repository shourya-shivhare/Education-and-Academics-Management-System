package edu.dbms.education.faculty;

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
 *   ADMIN              – full CRUD
 *   REGISTRAR/FACULTY  – read only
 *   STUDENT            – no access
 */
@RestController
@RequestMapping("/api/faculty")
@RequiredArgsConstructor
public class FacultyController {

    private final FacultyService  service;
    private final SqlTraceService trace;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseEnvelope<FacultyDto>> create(
            @Valid @RequestBody CreateFacultyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseEnvelope.ok(service.create(req), trace.getEntries()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR') or " +
                  "(hasRole('FACULTY') and #id == authentication.principal.personId)")
    public ResponseEntity<ResponseEnvelope<FacultyDto>> getById(@PathVariable int id) {
        return ResponseEntity.ok(ResponseEnvelope.ok(service.getById(id), trace.getEntries()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR','FACULTY')")
    public ResponseEntity<ResponseEnvelope<List<FacultyDto>>> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer departmentId,
            @Valid PageRequest page) {
        return ResponseEntity.ok(ResponseEnvelope.ok(
                service.search(name, departmentId, page), trace.getEntries()));
    }

    /** Faculty can view their own profile. */
    @GetMapping("/me")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<ResponseEnvelope<FacultyDto>> me() {
        return ResponseEntity.ok(ResponseEnvelope.ok(
                service.getById(AuthenticatedUser.currentPersonId()), trace.getEntries()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseEnvelope<FacultyDto>> update(
            @PathVariable int id,
            @Valid @RequestBody UpdateFacultyRequest req) {
        return ResponseEntity.ok(ResponseEnvelope.ok(service.update(id, req), trace.getEntries()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseEnvelope<Void>> delete(@PathVariable int id) {
        service.delete(id);
        return ResponseEntity.ok(ResponseEnvelope.ok(null, trace.getEntries()));
    }
}
