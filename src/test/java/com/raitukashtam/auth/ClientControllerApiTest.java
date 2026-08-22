package com.raitukashtam.auth;

import com.raitukashtam.auth.support.AbstractIntegrationTest;
import com.raitukashtam.auth.support.PkceFlowClient;
import com.raitukashtam.auth.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClientControllerApiTest extends AbstractIntegrationTest {

    private static final String WEB_CLIENT_ID = "raitukashtam-web";
    private static final String WEB_REDIRECT_URI = "http://localhost:3000/callback";
    private static final String DEFAULT_PRODUCT = "RAITUKASHTAM";

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void createClient_noToken_returns401() {
        assertThat(createClient(DEFAULT_PRODUCT, clientBody("WEB_SPA", List.of("https://x/callback")), null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createClient_nonAdminToken_returns403() {
        String token = userToken("client-nonadmin");
        assertThat(createClient(DEFAULT_PRODUCT, clientBody("WEB_SPA", List.of("https://x/callback")), token)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createClient_webSpaWithRedirectUri_returns201() {
        String token = adminToken("client-web-ok");

        ResponseEntity<String> response = createClient(DEFAULT_PRODUCT,
                clientBody("WEB_SPA", List.of("https://example.com/callback")), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).doesNotContain("\"clientSecret\":\"");
    }

    @Test
    void createClient_webSpaWithoutRedirectUri_returns400() {
        String token = adminToken("client-web-missing-redirect");

        ResponseEntity<String> response = createClient(DEFAULT_PRODUCT,
                clientBody("WEB_SPA", List.of()), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createClient_backendServiceWithRedirectUri_returns400() {
        String token = adminToken("client-backend-with-redirect");

        ResponseEntity<String> response = createClient(DEFAULT_PRODUCT,
                clientBody("BACKEND_SERVICE", List.of("https://should-not-be-here/callback")), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createClient_backendServiceWithoutRedirectUri_returns201WithSecret() {
        String token = adminToken("client-backend-ok");

        ResponseEntity<String> response = createClient(DEFAULT_PRODUCT,
                clientBody("BACKEND_SERVICE", List.of()), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // The one-time plaintext secret IS returned on create (only on create).
        assertThat(response.getBody()).contains("clientSecret");
    }

    @Test
    void createClient_duplicateClientId_returns409() {
        String token = adminToken("client-dup");
        String clientId = "dup-client-" + System.nanoTime();
        Map<String, Object> body = clientBodyWithId(clientId, "WEB_SPA", List.of("https://example.com/callback"));
        assertThat(createClient(DEFAULT_PRODUCT, body, token).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> response = createClient(DEFAULT_PRODUCT, body, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createClient_unknownProduct_returns404() {
        String token = adminToken("client-unknown-product");

        ResponseEntity<String> response = createClient("NO-SUCH-PRODUCT",
                clientBody("WEB_SPA", List.of("https://example.com/callback")), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listClients_adminToken_includesSeededWebClient() {
        String token = adminToken("client-lister");

        ResponseEntity<String> response = get("/products/" + DEFAULT_PRODUCT + "/clients", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("raitukashtam-web");
        // The secret is never present on a list response, only on create.
        assertThat(response.getBody()).doesNotContain("clientSecret\":\"");
    }

    @Test
    void listClients_unknownProduct_returns404() {
        String token = adminToken("client-lister-404");

        ResponseEntity<String> response = get("/products/NO-SUCH-PRODUCT/clients", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- helpers ----------

    private Map<String, Object> clientBody(String clientType, List<String> redirectUris) {
        return clientBodyWithId("client-" + System.nanoTime(), clientType, redirectUris);
    }

    private Map<String, Object> clientBodyWithId(String clientId, String clientType, List<String> redirectUris) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("clientId", clientId);
        body.put("clientType", clientType);
        body.put("redirectUris", redirectUris);
        return body;
    }

    private ResponseEntity<String> createClient(String productCode, Map<String, Object> body, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.postForEntity(
                baseUrl("/products/" + productCode + "/clients"), new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> get(String path, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange(baseUrl(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String adminToken(String tag) {
        String email = testDataFactory.uniqueEmail(tag);
        testDataFactory.registerAndPromoteToPlatformAdmin(email);
        return new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, email, TestDataFactory.VALID_PASSWORD);
    }

    private String userToken(String tag) {
        String email = testDataFactory.uniqueEmail(tag);
        testDataFactory.registerUser(email);
        return new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, email, TestDataFactory.VALID_PASSWORD);
    }
}
