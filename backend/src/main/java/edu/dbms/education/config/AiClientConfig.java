package edu.dbms.education.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AiClientConfig {

    @Bean
    RestClient aiRestClient(
            RestClient.Builder builder,
            @Value("${app.ai-service-url:http://127.0.0.1:8001}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
