package com.raitukashtam.auth;

import com.raitukashtam.auth.entity.CredentialType;
import com.raitukashtam.auth.entity.IdentityCredential;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.repository.IdentityCredentialRepository;
import com.raitukashtam.auth.repository.ProductMembershipRepository;
import com.raitukashtam.auth.support.AbstractIntegrationTest;
import com.raitukashtam.auth.support.TestDataFactory;
import com.raitukashtam.auth.support.WireMockStubs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

class OtpControllerApiTest extends AbstractIntegrationTest {

    @Autowired
    private TestDataFactory testDataFactory;
    @Autowired
    private IdentityCredentialRepository identityCredentialRepository;
    @Autowired
    private ProductMembershipRepository productMembershipRepository;

    @BeforeEach
    void resetStubs() {
        resetWireMock();
        // MyCommunity is onboarded via the PLATFORM_ADMIN API in real
        // environments (no auto-seeding in application code any more --
        // see TestDataFactory.ensureMyCommunityProduct's javadoc), so tests
        // recreate just enough of it directly. Idempotent and cheap;
        // simplest to call unconditionally rather than track which of the
        // tests below actually reach the code path that needs it.
        testDataFactory.ensureMyCommunityProduct();
    }

    @Test
    void generate_success_returns200() {
        WireMockStubs.stubOtpGenerateSuccess();

        ResponseEntity<String> response = otpGenerate(uniqueMobile());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void generate_twoFactorFailure_returns500() {
        // OtpController has no try/catch around a failed 2Factor response --
        // it propagates as an unhandled RuntimeException, mapped by
        // GlobalExceptionHandler's catch-all to 500. Documenting the real
        // current behavior, not asserting it's ideal.
        WireMockStubs.stubOtpGenerateFailure();

        ResponseEntity<String> response = otpGenerate(uniqueMobile());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void generate_rateLimitedAfterFiveRequestsPerIp() {
        WireMockStubs.stubOtpGenerateSuccess();

        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> ok = otpGenerate(uniqueMobile());
            assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        ResponseEntity<String> sixth = otpGenerate(uniqueMobile());
        assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void verify_correctCode_returns200() {
        WireMockStubs.stubOtpGenerateSuccess();
        WireMockStubs.stubOtpVerifyBehavior();
        String mobile = uniqueMobile();
        assertThat(otpGenerate(mobile).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> response = otpVerify(mobile, WireMockStubs.CORRECT_OTP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void verify_wrongCode_returns400() {
        WireMockStubs.stubOtpGenerateSuccess();
        WireMockStubs.stubOtpVerifyBehavior();
        String mobile = uniqueMobile();
        assertThat(otpGenerate(mobile).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> response = otpVerify(mobile, "000000");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Invalid or expired OTP");
    }

    @Test
    void verify_noOutstandingSession_returns400() {
        WireMockStubs.stubOtpVerifyBehavior();

        ResponseEntity<String> response = otpVerify(uniqueMobile(), WireMockStubs.CORRECT_OTP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void verify_rateLimitedAfterTenRequestsPerIp() {
        WireMockStubs.stubOtpGenerateSuccess();
        WireMockStubs.stubOtpVerifyBehavior();
        String mobile = uniqueMobile();
        assertThat(otpGenerate(mobile).getStatusCode()).isEqualTo(HttpStatus.OK);

        for (int i = 0; i < 10; i++) {
            ResponseEntity<String> attempt = otpVerify(mobile, "000000");
            assertThat(attempt.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        ResponseEntity<String> eleventh = otpVerify(mobile, "000000");
        assertThat(eleventh.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ---------- POST /otp/login ----------

    @Test
    void login_newPhoneNumber_establishesSession() {
        WireMockStubs.stubOtpGenerateSuccess();
        WireMockStubs.stubOtpVerifyBehavior();
        String mobile = uniqueMobile();
        assertThat(otpGenerate(mobile).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> response = otpLogin(mobile, WireMockStubs.CORRECT_OTP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // A real session was established -- the response carries a Set-Cookie,
        // same proof-of-session GoogleControllerApiTest relies on.
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNotNull();
    }

    @Test
    void login_wrongCode_returns400() {
        WireMockStubs.stubOtpGenerateSuccess();
        WireMockStubs.stubOtpVerifyBehavior();
        String mobile = uniqueMobile();
        assertThat(otpGenerate(mobile).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> response = otpLogin(mobile, "000000");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void login_samePhoneNumberTwice_linksToSameIdentityNotADuplicate() {
        WireMockStubs.stubOtpGenerateSuccess();
        WireMockStubs.stubOtpVerifyBehavior();
        String mobile = uniqueMobile();

        assertThat(otpGenerate(mobile).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(otpLogin(mobile, WireMockStubs.CORRECT_OTP).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second login, same mobile number, a fresh OTP session (OTP is
        // single-use) -- must succeed again, not attempt to create a
        // duplicate Identity/User for a phone-only OTP_PHONE credential
        // that already exists (would 500 on the email/mobile unique
        // constraints if it tried).
        assertThat(otpGenerate(mobile).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> secondLogin = otpLogin(mobile, WireMockStubs.CORRECT_OTP);

        assertThat(secondLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void login_newPhoneNumber_assignsMyCommunityMembershipNotTheSharedDefault() {
        WireMockStubs.stubOtpGenerateSuccess();
        WireMockStubs.stubOtpVerifyBehavior();
        String mobile = uniqueMobile();
        assertThat(otpGenerate(mobile).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(otpLogin(mobile, WireMockStubs.CORRECT_OTP).getStatusCode()).isEqualTo(HttpStatus.OK);

        IdentityCredential credential = identityCredentialRepository
                .findByCredentialTypeAndExternalSubject(CredentialType.OTP_PHONE, mobile)
                .orElseThrow();
        var identityId = credential.getIdentity().getId();

        assertThat(productMembershipRepository.existsByIdentity_IdAndProduct_Code(identityId, "MYCOMMUNITY")).isTrue();
        // Not the generic default product other self-service flows (password
        // registration, Google login) still use -- this is the whole point of
        // giving mycommunity its own product.
        assertThat(productMembershipRepository.existsByIdentity_IdAndProduct_Code(identityId, "RAITUKASHTAM")).isFalse();
    }

    @Test
    void login_mobileAlreadyRegisteredWithPassword_linksToExistingIdentityAndAddsMyCommunityMembership() {
        User existingUser = testDataFactory.registerUser(testDataFactory.uniqueEmail("otp-links-existing"));
        String mobile = existingUser.getMobileNumber();
        var identityId = existingUser.getIdentity().getId();
        // Sanity check on the fixture: password registration lands in the
        // shared default product, not MyCommunity, until OTP login below.
        assertThat(productMembershipRepository.existsByIdentity_IdAndProduct_Code(identityId, "RAITUKASHTAM")).isTrue();
        assertThat(productMembershipRepository.existsByIdentity_IdAndProduct_Code(identityId, "MYCOMMUNITY")).isFalse();

        WireMockStubs.stubOtpGenerateSuccess();
        WireMockStubs.stubOtpVerifyBehavior();
        assertThat(otpGenerate(mobile).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> response = otpLogin(mobile, WireMockStubs.CORRECT_OTP);

        // Links to the existing password-registered account rather than
        // creating a duplicate Identity/User for the same mobile number
        // (which would violate User.mobileNumber's unique constraint if it
        // tried) -- this is the "OTP for an existing account" path, not just
        // "brand-new phone-only signup".
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // And the existing RAITUKASHTAM membership stays -- OTP login only
        // adds MyCommunity, it doesn't touch or replace what was there.
        assertThat(productMembershipRepository.existsByIdentity_IdAndProduct_Code(identityId, "RAITUKASHTAM")).isTrue();
        assertThat(productMembershipRepository.existsByIdentity_IdAndProduct_Code(identityId, "MYCOMMUNITY")).isTrue();
    }

    @Test
    void login_lockedAccount_returns403() {
        User user = testDataFactory.registerUser(testDataFactory.uniqueEmail("otp-locked"));
        testDataFactory.lockAccount(user);
        String mobile = user.getMobileNumber();

        WireMockStubs.stubOtpGenerateSuccess();
        WireMockStubs.stubOtpVerifyBehavior();
        assertThat(otpGenerate(mobile).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> response = otpLogin(mobile, WireMockStubs.CORRECT_OTP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void login_rateLimitedAfterTenRequestsPerIp() {
        WireMockStubs.stubOtpGenerateSuccess();
        WireMockStubs.stubOtpVerifyBehavior();
        String mobile = uniqueMobile();
        assertThat(otpGenerate(mobile).getStatusCode()).isEqualTo(HttpStatus.OK);

        for (int i = 0; i < 10; i++) {
            ResponseEntity<String> attempt = otpLogin(mobile, "000000");
            assertThat(attempt.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        ResponseEntity<String> eleventh = otpLogin(mobile, "000000");
        assertThat(eleventh.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ---------- helpers ----------

    private static long mobileSeq = 8_000_000_000L;

    private static synchronized String uniqueMobile() {
        return String.valueOf(++mobileSeq);
    }

    private ResponseEntity<String> otpGenerate(String mobileNumber) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mobileNumber", mobileNumber);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return restTemplate.postForEntity(baseUrl("/otp/generate"), new HttpEntity<>(form, headers), String.class);
    }

    private ResponseEntity<String> otpVerify(String mobileNumber, String otp) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mobileNumber", mobileNumber);
        form.add("otp", otp);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return restTemplate.postForEntity(baseUrl("/otp/verify"), new HttpEntity<>(form, headers), String.class);
    }

    private ResponseEntity<String> otpLogin(String mobileNumber, String otp) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mobileNumber", mobileNumber);
        form.add("otp", otp);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return restTemplate.postForEntity(baseUrl("/otp/login"), new HttpEntity<>(form, headers), String.class);
    }
}
