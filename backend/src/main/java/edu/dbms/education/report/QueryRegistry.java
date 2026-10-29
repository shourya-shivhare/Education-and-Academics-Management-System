package edu.dbms.education.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads all 22 named queries from the classpath resource
 * {@code queries/queries.json} at application startup and keeps them
 * in an ordered map (insertion order = q01 … q22) for the lifetime of
 * the JVM.
 *
 * DBMS concept: the SQL text is stored separately from the application
 * code – in a structured JSON file – and loaded read-only at startup.
 * This is analogous to keeping stored-procedure definitions in migration
 * files: the database artefacts and the application code are decoupled.
 *
 * The queries.json file is copied to
 * {@code backend/src/main/resources/queries/queries.json} so it is
 * available on the classpath inside the fat jar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryRegistry {

    private static final String RESOURCE_PATH = "queries/queries.json";

    private final ObjectMapper objectMapper;

    // Ordered by insertion (q01 first, q22 last) – LinkedHashMap preserves order
    private Map<String, QueryDefinition> registry = Collections.emptyMap();

    @PostConstruct
    public void load() {
        try {
            InputStream stream = new ClassPathResource(RESOURCE_PATH).getInputStream();
            List<QueryDefinition> list = objectMapper.readValue(
                    stream, new TypeReference<>() {});

            Map<String, QueryDefinition> map = new LinkedHashMap<>();
            list.forEach(q -> map.put(q.getId(), q));
            this.registry = Collections.unmodifiableMap(map);
            log.info("QueryRegistry: loaded {} queries from {}", map.size(), RESOURCE_PATH);
        } catch (IOException e) {
            log.error("QueryRegistry: failed to load {}: {}", RESOURCE_PATH, e.getMessage());
            // Non-fatal – registry stays empty; report endpoints return 404
        }
    }

    /** Returns all query definitions in declaration order. */
    public List<QueryDefinition> all() {
        return List.copyOf(registry.values());
    }

    /** Looks up a single query by id (e.g. "q01"). */
    public Optional<QueryDefinition> find(String id) {
        return Optional.ofNullable(registry.get(id));
    }
}
