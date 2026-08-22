package com.raitukashtam.auth;

import com.raitukashtam.auth.support.AbstractIntegrationTest;
import com.raitukashtam.auth.support.PkceFlowClient;
import com.raitukashtam.auth.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

/**
 * emailService (inherited from AbstractIntegrationTest, a shared @Primary
 * mock bean rather than @MockBean -- see that class's javadoc for why)
 * stands in for the real JavaMailSender rather than letting it attempt a
 * genuine SMTP send: (1) ForgotPasswordController's own catch-all means a
 * real send failure is invisible at the HTTP layer anyway (always 200), so
 * it wouldn't prove anything either way, and (2) this is the only way to
 * actually capture the real reset token value needed to drive the
 * follow-up /reset-password call.
 */
class ForgotPasswordControllerApiTest extends AbstractIntegrationTest {

    private static final String WEB_CLIENT_ID = "raitukashtam-web";
    private static final String WEB_REDIRECT_URI = "http://localhost:3000/callback";

    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    void forgotPassword_knownEmail_returns200AndSendsToken() {
        doNothing().when(emailService).sendPasswordResetEmail(anyString(), anyString());
        String email = testDataFactory.uniqueEmail("forgot-known");
        testDataFactory.registerUser(email);

        ResponseEntity<String> response = forgotPassword(email);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(emailService).sendPasswordResetEmail(eqIgnoreCase(email), anyString());
    }

    @Test
    void forgotPassword_unknownEmail_stillReturns200() {
        // Deliberately doesn't reveal whether the email is registered.
        ResponseEntity<String> response = forgotPassword(testDataFactory.uniqueEmail("never-registered"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void forgotPassword_rateLimitedAfterFiveRequestsPerIp() {
        doNothing().when(emailService).sendPasswordResetEmail(anyString(), anyString());
        for (int i = 0; i < 5; i++) {
            assertThat(forgotPassword(testDataFactory.uniqueEmail("rl")).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        assertThat(forgotPassword(testDataFactory.uniqueEmail("rl")).getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void resetPassword_validToken_changesPasswordAndAllowsLogin() {
        String email = testDataFactory.uniqueEmail("reset-ok");
        testDataFactory.registerUser(email);
        String token = requestAndCaptureResetToken(email);

        String newPassword = "NewPassw0rd@";
        ResponseEntity<String> response = resetPassword(token, newPassword);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Prove the password was ACTUALLY changed, not just that the
        // endpoint said 200 -- log in with the new password for real.
        String accessToken = new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, email, newPassword);
        assertThat(accessToken).isNotBlank();
    }

    @Test
    void resetPassword_replayedToken_returns400() {
        String email = testDataFactory.uniqueEmail("reset-replay");
        testDataFactory.registerUser(email);
        String token = requestAndCaptureResetToken(email);

        assertThat(resetPassword(token, "FirstNew1@").getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> replay = resetPassword(token, "SecondNew1@");

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resetPassword_invalidToken_returns400() {
        ResponseEntity<String> response = resetPassword("not-a-real-token", "SomeNew1@");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resetPassword_rateLimitedAfterTenRequestsPerIp() {
        for (int i = 0; i < 10; i++) {
            assertThat(resetPassword("bogus-token", "SomeNew1@").getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(resetPassword("bogus-token", "SomeNew1@").getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ---------- helpers ----------

    private String requestAndCaptureResetToken(String email) {
        doNothing().when(emailService).sendPasswordResetEmail(anyString(), anyString());
        assertThat(forgotPassword(email).getStatusCode()).isEqualTo(HttpStatus.OK);

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eqIgnoreCase(email), tokenCaptor.capture());
        return tokenCaptor.getValue();
    }

    private static String eqIgnoreCase(String value) {
        return org.mockito.ArgumentMatchers.argThat((String arg) -> value.equalsIgnoreCase(arg));
    }

    private ResponseEntity<String> forgotPassword(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(
                baseUrl("/forgot-password"), new HttpEntity<>(Map.of("email", email), headers), String.class);
    }

    private ResponseEntity<String> resetPassword(String token, String newPassword) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("token", token, "newPassword", newPassword);
        return restTemplate.postForEntity(baseUrl("/reset-password"), new HttpEntity<>(body, headers), String.class);
    }
}
