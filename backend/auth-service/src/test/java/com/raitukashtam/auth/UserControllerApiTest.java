package com.raitukashtam.auth;

import com.raitukashtam.auth.entity.User;
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

class UserControllerApiTest extends AbstractIntegrationTest {

    private static final String WEB_CLIENT_ID = "raitukashtam-web";
    private static final String WEB_REDIRECT_URI = "http://localhost:3000/callback";

    @Autowired
    private TestDataFactory testDataFactory;

    // ---------- POST /users/register ----------

    @Test
    void register_success() {
        String email = testDataFactory.uniqueEmail("register-ok");
        Map<String, String> body = Map.of(
                "email", email,
                "password", TestDataFactory.VALID_PASSWORD,
                "firstName", "Ada",
                "lastName", "Lovelace",
                "mobileNumber", testDataFactory.uniqueMobile());

        ResponseEntity<String> response = postJson("/users/register", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("registered successfully");
    }

    @Test
    void register_duplicateEmail_returns409() {
        String email = testDataFactory.uniqueEmail("dup-email");
        testDataFactory.registerUser(email);

        Map<String, String> body = Map.of(
                "email", email,
                "password", TestDataFactory.VALID_PASSWORD,
                "firstName", "Ada",
                "lastName", "Lovelace",
                "mobileNumber", testDataFactory.uniqueMobile());

        ResponseEntity<String> response = postJson("/users/register", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void register_duplicateMobile_returns409() {
        String mobile = testDataFactory.uniqueMobile();
        testDataFactory.registerUser(testDataFactory.uniqueEmail("owner-of-mobile"));
        // registerUser() generates its own mobile; register a second one
        // sharing the SAME mobile explicitly to trigger the conflict.
        Map<String, String> first = Map.of(
                "email", testDataFactory.uniqueEmail("mobile-a"),
                "password", TestDataFactory.VALID_PASSWORD,
                "firstName", "A", "lastName", "A",
                "mobileNumber", mobile);
        assertThat(postJson("/users/register", first).getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, String> second = Map.of(
                "email", testDataFactory.uniqueEmail("mobile-b"),
                "password", TestDataFactory.VALID_PASSWORD,
                "firstName", "B", "lastName", "B",
                "mobileNumber", mobile);
        ResponseEntity<String> response = postJson("/users/register", second);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void register_weakPassword_returns400() {
        Map<String, String> body = Map.of(
                "email", testDataFactory.uniqueEmail("weak-pw"),
                "password", "weak", // fails the 8-18 char + uppercase + digit + special-char rule
                "firstName", "Ada",
                "lastName", "Lovelace",
                "mobileNumber", testDataFactory.uniqueMobile());

        ResponseEntity<String> response = postJson("/users/register", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("password");
    }

    @Test
    void register_invalidEmailFormat_returns400() {
        Map<String, String> body = Map.of(
                "email", "not-an-email",
                "password", TestDataFactory.VALID_PASSWORD,
                "firstName", "Ada",
                "lastName", "Lovelace",
                "mobileNumber", testDataFactory.uniqueMobile());

        ResponseEntity<String> response = postJson("/users/register", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void register_mobileNumberWrongLength_returns400() {
        Map<String, String> body = Map.of(
                "email", testDataFactory.uniqueEmail("bad-mobile"),
                "password", TestDataFactory.VALID_PASSWORD,
                "firstName", "Ada",
                "lastName", "Lovelace",
                "mobileNumber", "12345"); // not exactly 10 digits

        ResponseEntity<String> response = postJson("/users/register", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------- GET /users/me (Self-Service) ----------

    @Test
    void getCurrentUser_noToken_returns401() {
        assertThat(get("/users/me", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getCurrentUser_withValidUserToken_returns200() {
        String email = testDataFactory.uniqueEmail("me-ok");
        testDataFactory.registerUser(email);
        String token = new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, email, TestDataFactory.VALID_PASSWORD);

        ResponseEntity<String> response = get("/users/me", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(email);
    }

    @Test
    void getCurrentUser_withBackendServiceToken_returns404() {
        // client_credentials tokens have no Identity behind them -- their
        // `sub` is the OAuth2 client id, not an Identity UUID -- so /me has
        // no profile to return, same as an unknown identity.
        String clientId = "test-backend-me-" + System.nanoTime();
        String secret = testDataFactory.createBackendServiceClient(clientId);
        PkceFlowClient.TokenResult token = new PkceFlowClient(restTemplate).clientCredentials(baseUrl(""), clientId, secret);
        assertThat(token.accessToken()).isNotNull();

        ResponseEntity<String> response = get("/users/me", token.accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- GET /users/{id} (Business Service Integration) ----------

    @Test
    void getUserById_noToken_returns401() {
        User user = testDataFactory.registerUser(testDataFactory.uniqueEmail("lookup-noauth"));

        ResponseEntity<String> response = get("/users/" + user.getId(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getUserById_withValidUserToken_returns200() {
        String email = testDataFactory.uniqueEmail("lookup-ok");
        User user = testDataFactory.registerUser(email);
        String token = new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, email, TestDataFactory.VALID_PASSWORD);

        ResponseEntity<String> response = get("/users/" + user.getId(), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(email);
    }

    @Test
    void getUserById_withBackendServiceToken_returns200() {
        // The actual intended caller for this endpoint: a business service
        // authenticating via client_credentials, not a logged-in user.
        User user = testDataFactory.registerUser(testDataFactory.uniqueEmail("lookup-svc"));
        String clientId = "test-backend-" + System.nanoTime();
        String secret = testDataFactory.createBackendServiceClient(clientId);
        PkceFlowClient.TokenResult token = new PkceFlowClient(restTemplate).clientCredentials(baseUrl(""), clientId, secret);
        assertThat(token.accessToken()).isNotNull();

        ResponseEntity<String> response = get("/users/" + user.getId(), token.accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getUserById_unknownId_returns404() {
        String email = testDataFactory.uniqueEmail("lookup-404");
        testDataFactory.registerUser(email);
        String token = new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, email, TestDataFactory.VALID_PASSWORD);

        ResponseEntity<String> response = get("/users/999999999", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- GET /users (Platform Admin) ----------

    @Test
    void getAllUsers_noToken_returns401() {
        assertThat(get("/users", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getAllUsers_nonAdminToken_returns403() {
        String email = testDataFactory.uniqueEmail("list-nonadmin");
        testDataFactory.registerUser(email);
        String token = new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, email, TestDataFactory.VALID_PASSWORD);

        assertThat(get("/users", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getAllUsers_adminToken_returns200() {
        String email = testDataFactory.uniqueEmail("list-admin");
        testDataFactory.registerAndPromoteToPlatformAdmin(email);
        String token = new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, email, TestDataFactory.VALID_PASSWORD);

        ResponseEntity<String> response = get("/users", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(email);
    }

    // ---------- PATCH /users/{id}/platform-admin (Platform Admin) ----------

    @Test
    void setPlatformAdmin_noToken_returns401() {
        User target = testDataFactory.registerUser(testDataFactory.uniqueEmail("promote-noauth"));
        assertThat(patchPlatformAdmin(target.getId(), true, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void setPlatformAdmin_nonAdminToken_returns403() {
        String email = testDataFactory.uniqueEmail("promote-nonadmin");
        testDataFactory.registerUser(email);
        String token = new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, email, TestDataFactory.VALID_PASSWORD);
        User target = testDataFactory.registerUser(testDataFactory.uniqueEmail("promote-target"));

        assertThat(patchPlatformAdmin(target.getId(), true, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void setPlatformAdmin_adminToken_promotesAndDemotes() {
        String adminEmail = testDataFactory.uniqueEmail("promoter");
        testDataFactory.registerAndPromoteToPlatformAdmin(adminEmail);
        String adminToken = new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, adminEmail, TestDataFactory.VALID_PASSWORD);
        User target = testDataFactory.registerUser(testDataFactory.uniqueEmail("promote-target-2"));

        ResponseEntity<String> promoted = patchPlatformAdmin(target.getId(), true, adminToken);
        assertThat(promoted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(promoted.getBody()).contains("\"platformAdmin\":true");

        ResponseEntity<String> demoted = patchPlatformAdmin(target.getId(), false, adminToken);
        assertThat(demoted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(demoted.getBody()).contains("\"platformAdmin\":false");
    }

    @Test
    void setPlatformAdmin_unknownUser_returns404() {
        String adminEmail = testDataFactory.uniqueEmail("promoter-404");
        testDataFactory.registerAndPromoteToPlatformAdmin(adminEmail);
        String adminToken = new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, adminEmail, TestDataFactory.VALID_PASSWORD);

        assertThat(patchPlatformAdmin(999999999L, true, adminToken).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---------- helpers ----------

    private ResponseEntity<String> postJson(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(baseUrl(path), new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> get(String path, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange(baseUrl(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> patchPlatformAdmin(long userId, boolean platformAdmin, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        HttpEntity<Map<String, Boolean>> entity = new HttpEntity<>(Map.of("platformAdmin", platformAdmin), headers);
        return restTemplate.exchange(baseUrl("/users/" + userId + "/platform-admin"), HttpMethod.PATCH, entity, String.class);
    }
}
