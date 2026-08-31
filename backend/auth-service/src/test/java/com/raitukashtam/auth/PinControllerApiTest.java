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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class PinControllerApiTest extends AbstractIntegrationTest {

    private static final String WEB_CLIENT_ID = "raitukashtam-web";
    private static final String WEB_REDIRECT_URI = "http://localhost:3000/callback";
    private static final String VALID_PIN = "1234";

    @Autowired
    private TestDataFactory testDataFactory;

    // ---------- POST /pin/register ----------

    @Test
    void register_noToken_returns401() {
        assertThat(registerPin(null, uniqueDeviceId(), VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void register_deviceAlreadyRegisteredToDifferentIdentity_returns409() {
        String tokenA = tokenForNewUser("pin-owner-a");
        String deviceId = uniqueDeviceId();
        assertThat(registerPin(tokenA, deviceId, VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.OK);

        String tokenB = tokenForNewUser("pin-owner-b");
        ResponseEntity<String> response = registerPin(tokenB, deviceId, "5678");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void register_sameDeviceAgain_updatesThePin() {
        String token = tokenForNewUser("pin-change");
        String deviceId = uniqueDeviceId();
        assertThat(registerPin(token, deviceId, "1111").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registerPin(token, deviceId, "2222").getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(loginWithPin(deviceId, "1111").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(loginWithPin(deviceId, "2222").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---------- POST /pin/login ----------

    @Test
    void register_thenLoginWithPin_establishesSession() {
        String token = tokenForNewUser("pin-login-ok");
        String deviceId = uniqueDeviceId();
        assertThat(registerPin(token, deviceId, VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> response = loginWithPin(deviceId, VALID_PIN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Same proof-of-session OtpControllerApiTest/GoogleControllerApiTest rely on.
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNotNull();
    }

    @Test
    void login_unregisteredDevice_returns404() {
        assertThat(loginWithPin(uniqueDeviceId(), VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void login_wrongPin_returns400() {
        String token = tokenForNewUser("pin-wrong");
        String deviceId = uniqueDeviceId();
        assertThat(registerPin(token, deviceId, VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(loginWithPin(deviceId, "9999").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void login_wrongPinRepeatedly_locksDeviceAfterMaxAttempts() {
        String token = tokenForNewUser("pin-lockout");
        String deviceId = uniqueDeviceId();
        assertThat(registerPin(token, deviceId, VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.OK);

        // PinSecurityConfig defaults to 5 max attempts (no override in
        // application-apitest.yml).
        for (int i = 0; i < 5; i++) {
            assertThat(loginWithPin(deviceId, "0000").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        ResponseEntity<String> sixth = loginWithPin(deviceId, "0000");
        assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Even the CORRECT pin is now refused until the lockout window
        // passes -- checkNotLocked runs before credential verification.
        ResponseEntity<String> correctButLocked = loginWithPin(deviceId, VALID_PIN);
        assertThat(correctButLocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void login_rateLimitedAfterTenRequestsPerIp() {
        // Isolated from the per-device lockout above by using a fresh,
        // never-registered deviceId each call (each 404s "no such device"
        // rather than "wrong pin") -- RateLimiterService.checkLimit is
        // IP-keyed and runs before the credential lookup either way, so it
        // still counts every call regardless of device.
        for (int i = 0; i < 10; i++) {
            assertThat(loginWithPin(uniqueDeviceId(), VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        ResponseEntity<String> eleventh = loginWithPin(uniqueDeviceId(), VALID_PIN);
        assertThat(eleventh.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void login_lockedAccount_returns403() {
        String email = testDataFactory.uniqueEmail("pin-account-locked");
        User user = testDataFactory.registerUser(email);
        String token = new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, email, TestDataFactory.VALID_PASSWORD);
        String deviceId = uniqueDeviceId();
        assertThat(registerPin(token, deviceId, VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.OK);

        testDataFactory.lockAccount(user);

        assertThat(loginWithPin(deviceId, VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---------- GET /pin/devices, DELETE /pin/devices/{deviceId} ----------

    @Test
    void listDevices_returnsOnlyCallersOwnDevices() {
        String tokenA = tokenForNewUser("pin-list-a");
        String deviceIdA = uniqueDeviceId();
        assertThat(registerPin(tokenA, deviceIdA, VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.OK);

        String tokenB = tokenForNewUser("pin-list-b");
        assertThat(registerPin(tokenB, uniqueDeviceId(), VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> response = listDevices(tokenA);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(deviceIdA);
    }

    @Test
    void selfRevoke_ownDevice_thenLoginReturns404() {
        String token = tokenForNewUser("pin-self-revoke");
        String deviceId = uniqueDeviceId();
        assertThat(registerPin(token, deviceId, VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(deleteOwnDevice(token, deviceId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(loginWithPin(deviceId, VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void selfRevoke_anotherIdentitysDevice_returns404() {
        String tokenA = tokenForNewUser("pin-cant-touch-a");
        String deviceIdA = uniqueDeviceId();
        assertThat(registerPin(tokenA, deviceIdA, VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.OK);

        String tokenB = tokenForNewUser("pin-cant-touch-b");

        assertThat(deleteOwnDevice(tokenB, deviceIdA).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Untouched -- the other identity's device still works.
        assertThat(loginWithPin(deviceIdA, VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---------- DELETE /users/{id}/pin-devices/{deviceId} (Platform Admin) ----------

    @Test
    void adminRevoke_removesAnotherIdentitysDevice() {
        String adminEmail = testDataFactory.uniqueEmail("pin-admin-revoker");
        testDataFactory.registerAndPromoteToPlatformAdmin(adminEmail);
        String adminToken = new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, adminEmail, TestDataFactory.VALID_PASSWORD);

        String targetEmail = testDataFactory.uniqueEmail("pin-admin-target");
        User target = testDataFactory.registerUser(targetEmail);
        String targetToken = new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, targetEmail, TestDataFactory.VALID_PASSWORD);
        String deviceId = uniqueDeviceId();
        assertThat(registerPin(targetToken, deviceId, VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> revoke = adminRevokeDevice(adminToken, target.getId(), deviceId);
        assertThat(revoke.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(loginWithPin(deviceId, VALID_PIN).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void adminRevoke_nonAdminToken_returns403() {
        String callerToken = tokenForNewUser("pin-admin-nonadmin");
        String targetEmail = testDataFactory.uniqueEmail("pin-admin-target-2");
        User target = testDataFactory.registerUser(targetEmail);

        assertThat(adminRevokeDevice(callerToken, target.getId(), uniqueDeviceId()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---------- helpers ----------

    private static final AtomicLong DEVICE_SEQ = new AtomicLong(0);

    private static synchronized String uniqueDeviceId() {
        return "test-device-" + DEVICE_SEQ.incrementAndGet() + "-" + System.nanoTime();
    }

    private String tokenForNewUser(String emailPrefix) {
        String email = testDataFactory.uniqueEmail(emailPrefix);
        testDataFactory.registerUser(email);
        return new PkceFlowClient(restTemplate)
                .loginAndGetAccessToken(baseUrl(""), WEB_CLIENT_ID, WEB_REDIRECT_URI, email, TestDataFactory.VALID_PASSWORD);
    }

    private ResponseEntity<String> registerPin(String token, String deviceId, String pin) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("deviceId", deviceId);
        form.add("pin", pin);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.postForEntity(baseUrl("/pin/register"), new HttpEntity<>(form, headers), String.class);
    }

    private ResponseEntity<String> loginWithPin(String deviceId, String pin) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("deviceId", deviceId);
        form.add("pin", pin);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return restTemplate.postForEntity(baseUrl("/pin/login"), new HttpEntity<>(form, headers), String.class);
    }

    private ResponseEntity<String> listDevices(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(baseUrl("/pin/devices"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> deleteOwnDevice(String token, String deviceId) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(baseUrl("/pin/devices/" + deviceId), HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> adminRevokeDevice(String token, long userId, String deviceId) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(baseUrl("/users/" + userId + "/pin-devices/" + deviceId), HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
    }
}
