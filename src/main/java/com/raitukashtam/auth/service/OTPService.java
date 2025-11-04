package com.raitukashtam.auth.service;

import com.raitukashtam.auth.model.OTP;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;

@Service
public class OTPService {
    @Value("${otp.expiry.minutes:10}")
    private int otpExpiryMinutes;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${twofactor.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String OTP_PREFIX = "otp:";

    public String generateAndSaveOTP(String mobileNumber) {
        String otp = String.format("%06d", new Random().nextInt(999999));
        OTP otpData = OTP.builder()
                .mobileNumber(mobileNumber)
                .otp(otp)
                .expiryTime(LocalDateTime.now().plusMinutes(otpExpiryMinutes))
                .build();

        redisTemplate.opsForValue().set(
                OTP_PREFIX + mobileNumber,
                otpData,
                Duration.ofMinutes(otpExpiryMinutes)
        );

        return otp;
    }

    public boolean validateOTP(String mobileNumber, String otp) {
        OTP storedOtp = (OTP) redisTemplate.opsForValue().get(OTP_PREFIX + mobileNumber);
        return storedOtp.getOtp().equals(otp) && LocalDateTime.now().isBefore(storedOtp.getExpiryTime());
    }

    public String sendOtp(String phoneNumber) {
        String url = String.format("https://2factor.in/API/V1/%s/SMS/%s/AUTOGEN", apiKey, phoneNumber);

        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Map<String, Object> body = response.getBody();

        if (body != null && "Success".equals(body.get("Status"))) {
            return (String) body.get("Details"); // Session ID
        } else {
            throw new RuntimeException("Failed to send OTP: " + body);
        }
    }
}
