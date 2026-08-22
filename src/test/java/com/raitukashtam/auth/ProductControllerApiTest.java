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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductControllerApiTest extends AbstractIntegrationTest {

    private static final String WEB_CLIENT_ID = "raitukashtam-web";
    private static final String WEB_REDIRECT_URI = "http://localhost:3000/callback";

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void createProduct_noToken_returns401() {
        assertThat(createProduct("X" + System.nanoTime(), "New Product", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createProduct_nonAdminToken_returns403() {
        String token = adminlessUserToken("prod-nonadmin");
        assertThat(createProduct("X" + System.nanoTime(), "New Product", token).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createProduct_adminToken_returns201() {
        String token = adminToken("prod-creator-1");
        String code = "PRODNEW" + (System.nanoTime() % 1_000_000);

        ResponseEntity<String> response = createProduct(code, "Brand New Product", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains(code);
    }

    @Test
    void createProduct_duplicateCode_returns409() {
        String token = adminToken("prod-creator-2");
        String code = "PRODDUP" + (System.nanoTime() % 1_000_000);
        assertThat(createProduct(code, "First", token).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> response = createProduct(code, "Second", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createProduct_invalidCodeFormat_returns400() {
        String token = adminToken("prod-creator-3");

        // Code pattern requires uppercase/digits/hyphens only.
        ResponseEntity<String> response = createProduct("lowercase-not-allowed", "Bad Code", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getAllProducts_adminToken_includesSeededDefault() {
        String token = adminToken("prod-lister");

        ResponseEntity<String> response = get("/products", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("RAITUKASHTAM");
    }

    @Test
    void getProductByCode_existingCode_returns200() {
        String token = adminToken("prod-getter");

        ResponseEntity<String> response = get("/products/RAITUKASHTAM", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getProductByCode_unknownCode_returns404() {
        String token = adminToken("prod-getter-404");

        ResponseEntity<String> response = get("/products/DOES-NOT-EXIST", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- helpers ----------

    protected String adminToken(String tag) {
        String email = testDataFactory.uniqueEmail(tag);
        testDataFactory.registerAndPromoteToPlatformAdmin(email);
        return new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, email, TestDataFactory.VALID_PASSWORD);
    }

    protected String adminlessUserToken(String tag) {
        String email = testDataFactory.uniqueEmail(tag);
        testDataFactory.registerUser(email);
        return new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, email, TestDataFactory.VALID_PASSWORD);
    }

    private ResponseEntity<String> createProduct(String code, String name, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        Map<String, String> body = Map.of("code", code, "name", name);
        return restTemplate.postForEntity(baseUrl("/products"), new HttpEntity<>(body, headers), String.class);
    }

    protected ResponseEntity<String> get(String path, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange(baseUrl(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
