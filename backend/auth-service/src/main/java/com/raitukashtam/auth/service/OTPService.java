package com.raitukashtam.auth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Delegates both OTP generation and verification to 2Factor.in's own
 * stateful AUTOGEN/VERIFY session flow, rather than generating a code
 * locally -- 2Factor generates the code and sends the SMS itself; this
 * service only tracks the session id it hands back, keyed by mobile
 * number in Redis with a TTL, and forwards the caller's guess to 2Factor's
 * own verify endpoint. Never holds or exposes the real OTP value.
 *
 * Fixes a real vulnerability: the previous implementation generated a
 * code locally and returned it directly in the HTTP response instead of
 * ever sending it by SMS -- anyone who could call the endpoint got the
 * code with no proof of phone possession. Not live-tested against a real
 * 2Factor account this session (would require sending a real SMS with an
 * already-rotated-out-of-caution API key) -- verified by code review and
 * against 2Factor's public API docs only, same caveat class as this
 * repo's other unverifiable-without-real-credentials integrations
 * (Google OAuth).
 */
@Service
public class OTPService {
    @Value("${otp.expiry.minutes:10}")
    private int otpExpiryMinutes;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${twofactor.api.key}")
    private String apiKey;

    @Value("${twofactor.api.url:https://2factor.in/API/V1/%%s/SMS/%%s/AUTOGEN}")
    private String apiUrlTemplate;

    @Value("${twofactor.api.verify-url:https://2factor.in/API/V1/%%s/SMS/VERIFY/%%s/%%s}")
    private String verifyUrlTemplate;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String SESSION_PREFIX = "otp-session:";

    /**
     * Triggers 2Factor to generate and SMS a code to mobileNumber, and
     * remembers the session id it returns (needed to verify later) --
     * never the code itself, which this service never sees.
     */
    public void generateAndSendOtp(String mobileNumber) {
        String url = String.format(apiUrlTemplate, apiKey, mobileNumber);
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Map<String, Object> body = response.getBody();

        if (body == null || !"Success".equals(body.get("Status"))) {
            throw new RuntimeException("Failed to send OTP: " + body);
        }

        String sessionId = (String) body.get("Details");
        redisTemplate.opsForValue().set(
                SESSION_PREFIX + mobileNumber, sessionId, Duration.ofMinutes(otpExpiryMinutes));
    }

    /**
     * Verifies the caller-supplied code against 2Factor's own VERIFY
     * endpoint for the outstanding session, if any. Single-use: the
     * session is consumed (deleted) on a successful match.
     */
    public boolean validateOtp(String mobileNumber, String otp) {
        String sessionId = redisTemplate.opsForValue().get(SESSION_PREFIX + mobileNumber);
        if (sessionId == null) {
            return false;
        }

        String url = String.format(verifyUrlTemplate, apiKey, sessionId, otp);
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Map<String, Object> body = response.getBody();
        boolean success = body != null && "Success".equals(body.get("Status"));

        if (success) {
            redisTemplate.delete(SESSION_PREFIX + mobileNumber);
        }
        return success;
    }
}
