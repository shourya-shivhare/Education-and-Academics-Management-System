package edu.dbms.education.ai;

import edu.dbms.education.api.ResponseEnvelope;
import edu.dbms.education.trace.SqlTraceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class AiRiskController {

    private final AiRiskService riskService;
    private final SqlTraceService traceService;

    @PostMapping("/predict")
    public ResponseEntity<ResponseEnvelope<Map<String, Object>>> predict(
            @Valid @RequestBody RiskPredictionRequest request) {
        return ResponseEntity.ok(ResponseEnvelope.ok(
                riskService.predict(request),
                traceService.getEntries()));
    }
}
