package ru.agenteec.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(@Value("${app.internal.secret:}") String internalSecret) {
        RestTemplate restTemplate = new RestTemplate();
        if (internalSecret != null && !internalSecret.isBlank()) {
            restTemplate.getInterceptors().add((request, body, execution) -> {
                request.getHeaders().add("X-Internal-Secret", internalSecret);
                return execution.execute(request, body);
            });
        }
        return restTemplate;
    }
}
