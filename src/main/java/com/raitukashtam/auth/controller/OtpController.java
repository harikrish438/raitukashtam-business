package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.service.OTPService;
import com.raitukashtam.auth.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OTPService otpService;
    private final RateLimiterService rateLimiterService;

    @PostMapping("/generate")
    public ResponseEntity<Void> generateOtp(@RequestParam String mobileNumber, HttpServletRequest request) {
        rateLimiterService.checkLimit("otp-generate", request, 5, Duration.ofHours(1));
        otpService.generateAndSendOtp(mobileNumber);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@RequestParam String mobileNumber,
                                        @RequestParam String otp,
                                        HttpServletRequest request) {
        rateLimiterService.checkLimit("otp-verify", request, 10, Duration.ofHours(1));
        if (otpService.validateOtp(mobileNumber, otp)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().body("Invalid or expired OTP");
    }
}
