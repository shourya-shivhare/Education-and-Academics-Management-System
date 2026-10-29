package edu.dbms.education.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration for the Vite development server.
 *
 * Origins are read from app.cors.allowed-origins in application.yml.
 * @Value cannot bind a YAML list directly, so the property is a single
 * comma-separated string and we split it here.
 *
 * Only /api/** is opened to CORS.  The same-origin policy still blocks any
 * random browser page from calling internal endpoints outside that prefix.
 */
@Configuration
public class CorsConfig {

    // Comma-separated string: "http://localhost:5173,http://localhost:5174"
    // @Value cannot bind YAML sequences; use a scalar + split instead.
    @Value("${app.cors.allowed-origins}")
    private String allowedOriginsRaw;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Split the comma-separated origins string into a list
        List<String> allowedOrigins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        // Explicit origins – no wildcard so credentials can be sent later
        config.setAllowedOrigins(allowedOrigins);

        // HTTP methods the Vite frontend will use
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Standard + custom headers
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "X-Internal-Key"   // required by the Python AI service proxy
        ));

        config.setAllowCredentials(true);

        // Cache preflight response for 1 hour
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        return new CorsFilter(source);
    }
}
