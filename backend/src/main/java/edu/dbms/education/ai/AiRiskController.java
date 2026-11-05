package edu.dbms.education.ai;

import edu.dbms.education.api.ResponseEnvelope;
import edu.dbms.education.trace.SqlTraceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class AiRiskController {

    private final RiskService riskService;
    private final SqlTraceService traceService;

    @PostMapping("/predict")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseEnvelope<Map<String, Object>>> predict(
            @Valid @RequestBody RiskPredictionRequest request) {
        return ResponseEntity.ok(ResponseEnvelope.ok(
                riskService.predict(request),
                traceService.getEntries()));
    }

    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR','FACULTY')")
    public ResponseEntity<ResponseEnvelope<Object>> run(@RequestParam int term) {
        return ResponseEntity.ok(ResponseEnvelope.ok(riskService.run(term), traceService.getEntries()));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseEnvelope<Object>> list(
            @RequestParam(required = false) Integer offeringId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Integer term) {
        return ResponseEntity.ok(ResponseEnvelope.ok(
                riskService.list(offeringId, level, term), traceService.getEntries()));
    }

    @GetMapping("/model-info")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseEnvelope<Object>> modelInfo() {
        return ResponseEntity.ok(ResponseEnvelope.ok(riskService.modelInfo(), traceService.getEntries()));
    }

    @GetMapping("/metrics")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseEnvelope<Object>> metrics() {
        return ResponseEntity.ok(ResponseEnvelope.ok(riskService.metrics(), traceService.getEntries()));
    }

    @PostMapping("/train")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseEnvelope<Object>> train() {
        return ResponseEntity.ok(ResponseEnvelope.ok(riskService.train(), traceService.getEntries()));
    }

    @GetMapping("/recommend/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','REGISTRAR','FACULTY') or " +
            "(hasRole('STUDENT') and #studentId == authentication.principal.personId)")
    public ResponseEntity<ResponseEnvelope<Object>> recommend(
            @PathVariable int studentId, @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ResponseEnvelope.ok(
                riskService.recommend(studentId, limit), traceService.getEntries()));
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseEnvelope<Object>> search(
            @RequestParam String q, @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ResponseEnvelope.ok(
                riskService.search(q, limit), traceService.getEntries()));
    }
}
