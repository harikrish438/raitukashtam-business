package com.raitukashtam.auth;

import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.security.GooglePayload;
import com.raitukashtam.auth.security.GoogleTokenVerifierService;
import com.raitukashtam.auth.support.AbstractIntegrationTest;
import com.raitukashtam.auth.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class GoogleControllerApiTest extends AbstractIntegrationTest {

    @Autowired
    private TestDataFactory testDataFactory;

    // googleTokenVerifierService is inherited from AbstractIntegrationTest
    // -- a shared @Primary mock bean, not @MockBean (see that class's
    // javadoc for why: @MockBean forks a distinct Spring context per class,
    // which collided on this suite's fixed test port).

    @Test
    void verifyToken_newGoogleUser_createsIdentityAndEstablishesSession() {
        String email = testDataFactory.uniqueEmail("google-new");
        when(googleTokenVerifierService.verify(eq("valid-new-token")))
                .thenReturn(Optional.of(new GooglePayload("google-sub-1", email, "New Googler", true)));

        ResponseEntity<Void> response = verifyToken("valid-new-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // A real session was established -- the response carries a Set-Cookie.
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNotNull();
    }

    @Test
    void verifyToken_existingGoogleCredential_linksToSameIdentity() {
        String email = testDataFactory.uniqueEmail("google-existing");
        when(googleTokenVerifierService.verify(eq("first-login")))
                .thenReturn(Optional.of(new GooglePayload("google-sub-2", email, "Repeat Googler", true)));
        assertThat(verifyToken("first-login").getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second sign-in with the same Google subject should succeed too,
        // not attempt to create a duplicate Identity/User.
        when(googleTokenVerifierService.verify(eq("second-login")))
                .thenReturn(Optional.of(new GooglePayload("google-sub-2", email, "Repeat Googler", true)));
        ResponseEntity<Void> response = verifyToken("second-login");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void verifyToken_unverifiedEmailConflictsWithExistingIdentity_returns403() {
        String email = testDataFactory.uniqueEmail("google-conflict");
        testDataFactory.registerUser(email); // existing password-based identity, same email

        when(googleTokenVerifierService.verify(eq("unverified-token")))
                .thenReturn(Optional.of(new GooglePayload("google-sub-3", email, "Impersonator?", false)));

        ResponseEntity<Void> response = verifyToken("unverified-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void verifyToken_invalidToken_returns400() {
        when(googleTokenVerifierService.verify(eq("garbage"))).thenReturn(Optional.empty());

        ResponseEntity<Void> response = verifyToken("garbage");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void verifyToken_verifierInfrastructureFailure_returns500() {
        when(googleTokenVerifierService.verify(eq("network-issue")))
                .thenThrow(new GoogleTokenVerifierService.GoogleVerificationException("boom", new RuntimeException()));

        ResponseEntity<Void> response = verifyToken("network-issue");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void verifyToken_lockedAccount_returns403() {
        String email = testDataFactory.uniqueEmail("google-locked");
        User user = testDataFactory.registerUser(email);
        testDataFactory.lockAccount(user);

        when(googleTokenVerifierService.verify(eq("locked-user-token")))
                .thenReturn(Optional.of(new GooglePayload("google-sub-locked", email, "Locked", true)));

        ResponseEntity<Void> response = verifyToken("locked-user-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void verifyToken_rateLimitedAfterTwentyRequestsPerIp() {
        when(googleTokenVerifierService.verify(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        for (int i = 0; i < 20; i++) {
            assertThat(verifyToken("attempt-" + i).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(verifyToken("one-too-many").getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private ResponseEntity<Void> verifyToken(String idToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("idToken", idToken);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return restTemplate.postForEntity(baseUrl("/google/verify-token"), new HttpEntity<>(form, headers), Void.class);
    }
}
