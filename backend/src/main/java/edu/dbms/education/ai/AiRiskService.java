package edu.dbms.education.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiRiskService {

    private final RestClient aiRestClient;

    @Value("${app.internal-key}")
    private String internalKey;

    public Map<String, Object> predict(RiskPredictionRequest request) {
        try {
            Map<String, Object> response = aiRestClient.post()
                    .uri("/predict/risk")
                    .header("X-Internal-Key", internalKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw new IllegalStateException("AI service returned an empty response.");
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw new AiServiceUnavailableException(
                    "AI service rejected the prediction request: " + ex.getResponseBodyAsString(), ex);
        } catch (RuntimeException ex) {
            throw new AiServiceUnavailableException("AI service is unavailable.", ex);
        }
    }
}
