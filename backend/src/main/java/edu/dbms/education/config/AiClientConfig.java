package edu.dbms.education.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class AiClientConfig {

    @Bean
    RestClient aiRestClient(
            RestClient.Builder builder,
            @Value("${app.ai-service-url:http://127.0.0.1:8001}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(30000);
        return builder.baseUrl(baseUrl).requestFactory(factory).build();
    }
}
