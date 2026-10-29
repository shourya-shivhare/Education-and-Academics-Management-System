package edu.dbms.education.attendance;

import edu.dbms.education.api.ResponseEnvelope;
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
 *   ADMIN/REGISTRAR – read only
 *   FACULTY         – create/update/delete for their own offerings (enforced in service)
 *   STUDENT         – read own attendance only (enforced in service)
 */
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService service;
    private final SqlTraceService   trace;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR','FACULTY')")
    public ResponseEntity<ResponseEnvelope<AttendanceDto>> create(
            @Valid @RequestBody RecordAttendanceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseEnvelope.ok(service.create(req), trace.getEntries()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseEnvelope<AttendanceDto>> getById(@PathVariable int id) {
        return ResponseEntity.ok(ResponseEnvelope.ok(service.getById(id), trace.getEntries()));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseEnvelope<List<AttendanceDto>>> search(
            @RequestParam(required = false) Integer enrollmentId,
            @RequestParam(required = false) Integer offeringId,
            @RequestParam(required = false) Integer studentId,
            @RequestParam(required = false) String  fromDate,
            @RequestParam(required = false) String  toDate,
            @Valid PageRequest page) {
        return ResponseEntity.ok(ResponseEnvelope.ok(
                service.search(enrollmentId, offeringId, studentId, fromDate, toDate, page),
                trace.getEntries()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR','FACULTY')")
    public ResponseEntity<ResponseEnvelope<AttendanceDto>> updateStatus(
            @PathVariable int id,
            @RequestBody StatusRequest body) {
        return ResponseEntity.ok(ResponseEnvelope.ok(
                service.updateStatus(id, body.getStatus()), trace.getEntries()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY')")
    public ResponseEntity<ResponseEnvelope<Void>> delete(@PathVariable int id) {
        service.delete(id);
        return ResponseEntity.ok(ResponseEnvelope.ok(null, trace.getEntries()));
    }

    @Getter @Setter
    static class StatusRequest { private String status; }
}
