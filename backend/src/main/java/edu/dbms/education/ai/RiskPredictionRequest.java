package edu.dbms.education.ai;

import jakarta.validation.constraints.Positive;

public record RiskPredictionRequest(
        @Positive(message = "studentId must be positive") Integer studentId,
        @Positive(message = "termId must be positive") Integer termId,
        java.util.Map<String, Double> features) {
}
