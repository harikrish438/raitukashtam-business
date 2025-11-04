package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.response.OtpResponse;
import com.raitukashtam.auth.service.OTPService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/otp")
public class OtpController {
    @Autowired
    private OTPService otpService;

    @PostMapping("/generate")
    public ResponseEntity<OtpResponse> generateOtp(@RequestParam String mobileNumber) {
        String otp = otpService.generateAndSaveOTP(mobileNumber);
        // TODO: Send OTP to user's email/phone
        return ResponseEntity.ok(new OtpResponse(otp));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@RequestParam String email,
                                       @RequestParam String otp) {
        if (otpService.validateOTP(email, otp)) {
            // Generate JWT token or session token here
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().body("Invalid or expired OTP");
    }
}
