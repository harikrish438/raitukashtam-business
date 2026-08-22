package com.raitukashtam.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.support.AbstractIntegrationTest;
import com.raitukashtam.auth.support.PkceFlowClient;
import com.raitukashtam.auth.support.TestDataFactory;
import com.raitukashtam.auth.support.WireMockStubs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the OAuth2/login mechanics directly, on top of the PkceFlowClient
 * infrastructure UserControllerApiTest etc. already rely on for setup --
 * this class is where that flow's own correctness (captcha escalation,
 * lockout, token claim shape, refresh-token absence for public clients) is
 * actually proven, not assumed.
 */
class AuthorizationFlowApiTest extends AbstractIntegrationTest {

    private static final String WEB_CLIENT_ID = "raitukashtam-web";
    private static final String WEB_REDIRECT_URI = "http://localhost:3000/callback";

    @Autowired
    private TestDataFactory testDataFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void stubRecaptcha() {
        resetWireMock();
        WireMockStubs.stubRecaptchaSuccess();
    }

    @Test
    void login_wrongPassword_redirectsWithBadCredentialsError() {
        String email = testDataFactory.uniqueEmail("wrongpw");
        testDataFactory.registerUser(email);
        PkceFlowClient client = new PkceFlowClient(restTemplate);
        client.startAuthorize(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI,
                PkceFlowClient.generatePkce().challenge(), "s");

        ResponseEntity<Void> result = client.submitLogin(baseUrl(""), email, "TotallyWrong1@", null);

        assertThat(result.getStatusCode().value()).isEqualTo(302);
        assertThat(result.getHeaders().getLocation().toString()).contains("/login").contains("error=bad_credentials");
    }

    @Test
    void login_correctPassword_issuesTokenWithConsumerRoleAndNoPlatformRole() {
        String email = testDataFactory.uniqueEmail("consumer-token");
        testDataFactory.registerUser(email);

        String token = new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, email, TestDataFactory.VALID_PASSWORD);

        Map<String, Object> claims = decodeJwtPayload(token);
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get("roles");
        assertThat(roles).contains("CONSUMER");
        assertThat(claims).doesNotContainKey("platform_role");
    }

    @Test
    void login_platformAdmin_issuesTokenWithPlatformRoleClaim() {
        String email = testDataFactory.uniqueEmail("admin-token");
        testDataFactory.registerAndPromoteToPlatformAdmin(email);

        String token = new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, email, TestDataFactory.VALID_PASSWORD);

        Map<String, Object> claims = decodeJwtPayload(token);
        assertThat(claims.get("platform_role")).isEqualTo("PLATFORM_ADMIN");
    }

    @Test
    void login_captchaRequiredAfterThreeFailures_thenLockedOutAfterFive() {
        String email = testDataFactory.uniqueEmail("escalation");
        testDataFactory.registerUser(email);
        PkceFlowClient client = new PkceFlowClient(restTemplate);
        client.startAuthorize(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI,
                PkceFlowClient.generatePkce().challenge(), "s");

        // Failures 1-3: no captcha needed yet (threshold is 3 PRIOR failures).
        for (int i = 0; i < 3; i++) {
            ResponseEntity<Void> attempt = client.submitLogin(baseUrl(""), email, "Wrong" + i + "@Aa", null);
            assertThat(attempt.getHeaders().getLocation().toString()).contains("error=bad_credentials");
        }

        // 4th attempt, still wrong password, NO captcha token -> gate blocks
        // before even checking the password.
        ResponseEntity<Void> needsCaptcha = client.submitLogin(baseUrl(""), email, "Wrong3@Aa", null);
        assertThat(needsCaptcha.getHeaders().getLocation().toString()).contains("error=captcha_required");

        // Same 4th/5th attempts, now WITH a (WireMock-stubbed-valid) captcha
        // token, still wrong password -> passes the gate, fails on
        // credentials, IS recorded as a real failure (unlike the
        // captcha-required response above).
        ResponseEntity<Void> failure4 = client.submitLogin(baseUrl(""), email, "Wrong4@Aa", "valid-captcha");
        assertThat(failure4.getHeaders().getLocation().toString()).contains("error=bad_credentials");
        ResponseEntity<Void> failure5 = client.submitLogin(baseUrl(""), email, "Wrong5@Aa", "valid-captcha");
        assertThat(failure5.getHeaders().getLocation().toString()).contains("error=bad_credentials");

        // 6th attempt: 5 prior failures within the window -> rolling
        // lockout kicks in BEFORE credentials are even checked, so even
        // the genuinely correct password + valid captcha still fails.
        ResponseEntity<Void> lockedOut =
                client.submitLogin(baseUrl(""), email, TestDataFactory.VALID_PASSWORD, "valid-captcha");
        assertThat(lockedOut.getStatusCode().value()).isEqualTo(302);
        assertThat(lockedOut.getHeaders().getLocation().toString()).contains("error=bad_credentials");
    }

    @Test
    void login_hardLockedAccount_cannotLoginEvenWithCorrectPassword() {
        String email = testDataFactory.uniqueEmail("hardlocked");
        User user = testDataFactory.registerUser(email);
        testDataFactory.lockAccount(user);

        PkceFlowClient client = new PkceFlowClient(restTemplate);
        client.startAuthorize(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI,
                PkceFlowClient.generatePkce().challenge(), "s");
        ResponseEntity<Void> result = client.submitLogin(baseUrl(""), email, TestDataFactory.VALID_PASSWORD, null);

        assertThat(result.getStatusCode().value()).isEqualTo(302);
        assertThat(result.getHeaders().getLocation().toString()).contains("error=bad_credentials");
    }

    @Test
    void clientCredentials_correctSecret_issuesTokenWithNoRolesClaim() {
        String clientId = "authflow-backend-" + System.nanoTime();
        String secret = testDataFactory.createBackendServiceClient(clientId);

        PkceFlowClient.TokenResult token = new PkceFlowClient(restTemplate).clientCredentials(baseUrl(""), clientId, secret);

        assertThat(token.status()).isEqualTo(200);
        assertThat(token.accessToken()).isNotBlank();
        Map<String, Object> claims = decodeJwtPayload(token.accessToken());
        // No Identity behind a client_credentials token -- OAuth2TokenClaimsCustomizer
        // deliberately skips roles/platform_role for this grant type.
        assertThat(claims).doesNotContainKey("roles");
        assertThat(claims).doesNotContainKey("platform_role");
    }

    @Test
    void clientCredentials_wrongSecret_returns401() {
        String clientId = "authflow-backend-wrong-" + System.nanoTime();
        testDataFactory.createBackendServiceClient(clientId);

        PkceFlowClient.TokenResult token =
                new PkceFlowClient(restTemplate).clientCredentials(baseUrl(""), clientId, "definitely-not-the-secret");

        assertThat(token.status()).isEqualTo(401);
        assertThat(token.accessToken()).isNull();
    }

    @Test
    void publicClientPkceFlow_doesNotIssueRefreshToken() {
        // Drives the flow manually (rather than loginAndGetAccessToken,
        // which only surfaces the access_token string) to inspect the raw
        // token response body for a refresh_token field. Confirms a known,
        // deliberate Spring AS default this codebase's design doc already
        // documents (see project memory, Phase 4) rather than assuming it.
        String email = testDataFactory.uniqueEmail("no-refresh");
        testDataFactory.registerUser(email);
        PkceFlowClient.PkcePair pkce = PkceFlowClient.generatePkce();
        PkceFlowClient client = new PkceFlowClient(restTemplate);
        client.startAuthorize(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, pkce.challenge(), "s");
        ResponseEntity<Void> loginResult = client.submitLogin(baseUrl(""), email, TestDataFactory.VALID_PASSWORD, null);
        ResponseEntity<Void> toRedirect = client.get(loginResult.getHeaders().getLocation().toString());
        String code = java.net.URI.create(toRedirect.getHeaders().getLocation().toString())
                .getQuery().replaceAll(".*code=([^&]+).*", "$1");

        PkceFlowClient.TokenResult token =
                client.exchangeCode(baseUrl(""), code, WEB_REDIRECT_URI, WEB_CLIENT_ID, pkce.verifier());

        assertThat(token.accessToken()).isNotBlank();
        assertThat(token.body()).doesNotContainKey("refresh_token");
    }

    @Test
    void jwks_endpointReturnsAtLeastOneKey() {
        ResponseEntity<Map> response = restTemplate.getForEntity(baseUrl("/oauth2/jwks"), Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        List<?> keys = (List<?>) response.getBody().get("keys");
        assertThat(keys).isNotEmpty();
    }

    private Map<String, Object> decodeJwtPayload(String jwt) {
        try {
            String payloadSegment = jwt.split("\\.")[1];
            byte[] decoded = Base64.getUrlDecoder().decode(payloadSegment);
            return objectMapper.readValue(new String(decoded, StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            throw new AssertionError("Could not decode JWT payload: " + jwt, e);
        }
    }
}
