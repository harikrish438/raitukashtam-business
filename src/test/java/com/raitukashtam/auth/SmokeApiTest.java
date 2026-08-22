package com.raitukashtam.auth;

import com.raitukashtam.auth.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeApiTest extends AbstractIntegrationTest {

    @Test
    void actuatorHealthIsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl("/actuator/health"), String.class);
        assertThat(response.getStatusCode().value())
                .as("health check body: %s", response.getBody())
                .isEqualTo(200);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void registerEndpointIsReachable() {
        // Just proves the context boots, Flyway ran, and the default product
        // (ProductDataSeeder) exists -- full registration scenarios are
        // covered in UserControllerApiTest. An empty JSON object fails
        // @Valid (missing required fields) -> 400 via GlobalExceptionHandler.
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var entity = new org.springframework.http.HttpEntity<>("{}", headers);
        ResponseEntity<String> response =
                restTemplate.postForEntity(baseUrl("/users/register"), entity, String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}
