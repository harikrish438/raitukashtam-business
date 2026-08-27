package com.raitukashtam.auth;

import com.raitukashtam.auth.support.AbstractIntegrationTest;
import com.raitukashtam.auth.support.WireMockStubs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

class OtpControllerApiTest extends AbstractIntegrationTest {

    @BeforeEach
    void resetStubs() {
        resetWireMock();
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
}
