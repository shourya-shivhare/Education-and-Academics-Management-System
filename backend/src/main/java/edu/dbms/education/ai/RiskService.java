package edu.dbms.education.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.MediaType;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RiskService {

    private final RiskRepository repository;
    private final RestClient aiRestClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${app.internal-key}")
    private String internalKey;

    public List<Map<String, Object>> run(int termId) {
        List<Map<String, Object>> rows = repository.features(termId);
        List<Map<String, Object>> requests = rows.stream().map(row -> {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("student_id", ((Number) row.get("student_id")).intValue());
            request.put("term_id", termId);
            return request;
        }).toList();
        if (requests.isEmpty()) return List.of();
        List<Map<String, Object>> predictions = aiPost("/predict-batch",
                Map.of("requests", requests), List.class);
        for (Map<String, Object> prediction : predictions) {
            int studentId = ((Number) prediction.get("student_id")).intValue();
            double probability = ((Number) prediction.get("probability")).doubleValue();
            String label = String.valueOf(prediction.get("risk_level"));
            String model = String.valueOf(prediction.get("model_version"));
            repository.save(studentId, termId, probability * 100.0,
                    capitalize(label), model, json(prediction.get("top_factors")));
        }
        return predictions;
    }

    public List<Map<String, Object>> list(Integer offeringId, String level, Integer termId) {
        return repository.list(offeringId, level, termId);
    }

    public Map<String, Object> predict(RiskPredictionRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("student_id", request.studentId());
        body.put("term_id", request.termId());
        if (request.features() != null) body.put("features", request.features());
        return aiPost("/predict", body, Map.class);
    }

    public Map<String, Object> modelInfo() { return aiGet("/model/info", Map.class); }
    public Map<String, Object> metrics() { return aiGet("/model/metrics", Map.class); }
    public Map<String, Object> train() { return aiPost("/train", Map.of(), Map.class); }
    public List<Map<String, Object>> recommend(int studentId, int limit) {
        return aiGet("/recommend/" + studentId + "?limit=" + limit, List.class);
    }
    public List<Map<String, Object>> search(String query, int limit) {
        return aiGet("/search?q=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8)
                + "&limit=" + limit, List.class);
    }

    private <T> T aiGet(String uri, Class<T> type) {
        try {
            T response = aiRestClient.get().uri(uri).header("X-Internal-Key", internalKey)
                    .retrieve().body(type);
            if (response == null) throw new IllegalStateException("AI service returned an empty response.");
            return response;
        } catch (RestClientResponseException ex) {
            throw unavailable("AI service request failed: " + ex.getResponseBodyAsString(), ex);
        } catch (RuntimeException ex) {
            throw unavailable("AI service is unavailable.", ex);
        }
    }

    private <T> T aiPost(String uri, Object body, Class<T> type) {
        try {
            T response = aiRestClient.post().uri(uri).header("X-Internal-Key", internalKey)
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(type);
            if (response == null) throw new IllegalStateException("AI service returned an empty response.");
            return response;
        } catch (RestClientResponseException ex) {
            throw unavailable("AI service request failed: " + ex.getResponseBodyAsString(), ex);
        } catch (RuntimeException ex) {
            throw unavailable("AI service is unavailable.", ex);
        }
    }

    private AiServiceUnavailableException unavailable(String message, RuntimeException cause) {
        return new AiServiceUnavailableException(message, cause);
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value == null ? List.of() : value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not serialize risk factors.", ex); }
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) return "Low";
        return value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase();
    }
}
