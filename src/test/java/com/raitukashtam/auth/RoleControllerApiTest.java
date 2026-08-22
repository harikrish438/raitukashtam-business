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

class RoleControllerApiTest extends AbstractIntegrationTest {

    private static final String WEB_CLIENT_ID = "raitukashtam-web";
    private static final String WEB_REDIRECT_URI = "http://localhost:3000/callback";
    private static final String DEFAULT_PRODUCT = "RAITUKASHTAM";

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void createRole_noToken_returns401() {
        assertThat(createRole(DEFAULT_PRODUCT, "ROLE_X" + System.nanoTime(), "Some Role", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createRole_nonAdminToken_returns403() {
        String token = userToken("role-nonadmin");
        assertThat(createRole(DEFAULT_PRODUCT, "ROLE_Y" + System.nanoTime(), "Some Role", token).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createRole_adminToken_returns201() {
        String token = adminToken("role-creator");
        String code = "ROLE_NEW" + (System.nanoTime() % 1_000_000);

        ResponseEntity<String> response = createRole(DEFAULT_PRODUCT, code, "Brand New Role", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains(code);
    }

    @Test
    void createRole_duplicateCode_returns409() {
        String token = adminToken("role-dup");
        String code = "ROLE_DUP" + (System.nanoTime() % 1_000_000);
        assertThat(createRole(DEFAULT_PRODUCT, code, "First", token).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> response = createRole(DEFAULT_PRODUCT, code, "Second", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createRole_invalidCodeFormat_returns400() {
        String token = adminToken("role-badcode");

        ResponseEntity<String> response = createRole(DEFAULT_PRODUCT, "lowercase-not-allowed", "Bad", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRole_unknownProduct_returns404() {
        String token = adminToken("role-unknown-product");

        ResponseEntity<String> response = createRole("NO-SUCH-PRODUCT", "ROLE_Z", "Role", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listRoles_adminToken_includesSeededDefaultRole() {
        String token = adminToken("role-lister");

        ResponseEntity<String> response = get("/products/" + DEFAULT_PRODUCT + "/roles", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // The default role every self-registered user gets (see RoleService.assignDefaultRole).
        assertThat(response.getBody()).contains("CONSUMER");
    }

    @Test
    void listRoles_unknownProduct_returns404() {
        String token = adminToken("role-lister-404");

        ResponseEntity<String> response = get("/products/NO-SUCH-PRODUCT/roles", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- helpers ----------

    private ResponseEntity<String> createRole(String productCode, String code, String name, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        Map<String, String> body = Map.of("code", code, "name", name);
        return restTemplate.postForEntity(
                baseUrl("/products/" + productCode + "/roles"), new HttpEntity<>(body, headers), String.class);
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
