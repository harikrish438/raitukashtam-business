package com.raitukashtam.auth;

import com.raitukashtam.auth.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeApiTest extends AbstractIntegrationTest {

    @Test
    void actuatorHealthIsUp() throws InterruptedException {
        // Spring's TestContext framework guarantees the context (including
        // finishRefresh()) is fully up before any @Test method runs, but on
        // a slower/shared CI CPU the very first HTTP request right after
        // that can still catch a health indicator mid-first-check -- 503
        // seen live in GitHub Actions (not locally), gone by the very next
        // request in this same class. A short retry, not a longer timeout,
        // since the state it's waiting on settles in well under a second.
        ResponseEntity<String> response = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            response = restTemplate.getForEntity(baseUrl("/actuator/health"), String.class);
            if (response.getStatusCode().value() == 200) {
                break;
            }
            Thread.sleep(300);
        }
        assertThat(response.getStatusCode().value()).isEqualTo(200);
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
