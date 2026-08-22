package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.config.OpenApiConfig;
import com.raitukashtam.auth.service.OTPService;
import com.raitukashtam.auth.service.RateLimiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/otp")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_SELF_SERVICE)
public class OtpController {

    private final OTPService otpService;
    private final RateLimiterService rateLimiterService;

    @PostMapping("/generate")
    @Operation(summary = "Send an OTP by SMS/voice call", description = "Public, unauthenticated, "
            + "rate-limited (5/hour/IP). Delegates to 2Factor.in's own AUTOGEN flow -- this service "
            + "never generates or sees the code.")
    public ResponseEntity<Void> generateOtp(@RequestParam String mobileNumber, HttpServletRequest request) {
        rateLimiterService.checkLimit("otp-generate", request, 5, Duration.ofHours(1));
        otpService.generateAndSendOtp(mobileNumber);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify an OTP code", description = "Public, unauthenticated, rate-limited "
            + "(10/hour/IP). Single-use -- the outstanding session is consumed on a successful match.")
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
