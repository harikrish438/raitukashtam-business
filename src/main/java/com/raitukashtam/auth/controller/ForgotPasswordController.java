package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.dto.ForgotPasswordRequest;
import com.raitukashtam.auth.dto.ResetPasswordRequest;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.entity.UsedPasswordResetToken;
import com.raitukashtam.auth.jwt.JwtTokenUtil;
import com.raitukashtam.auth.repository.UsedPasswordResetTokenRepository;
import com.raitukashtam.auth.service.EmailService;
import com.raitukashtam.auth.service.RateLimiterService;
import com.raitukashtam.auth.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
public class ForgotPasswordController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UsedPasswordResetTokenRepository usedPasswordResetTokenRepository;

    @Autowired
    private RateLimiterService rateLimiterService;

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        rateLimiterService.checkLimit("forgot-password", httpRequest, 5, Duration.ofHours(1));
        try {
            // Check if user exists with the given email
            User user = userService.findUserByEmail(request.getEmail());

            // Generate password reset token
            String resetToken = jwtTokenUtil.generatePasswordResetToken(user.getEmail());

            // Send password reset email
            emailService.sendPasswordResetEmail(user.getEmail(), resetToken);

            return ResponseEntity.ok().body("Password reset link has been sent to your email.");

        } catch (Exception e) {
            // Don't reveal that the email doesn't exist for security reasons
            return ResponseEntity.ok().body("If your email is registered, you will receive a password reset link.");
        }
    }

    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
        rateLimiterService.checkLimit("reset-password", httpRequest, 10, Duration.ofHours(1));
        try {
            // Validate the token and get the email + jti (single-use check)
            String email = jwtTokenUtil.getUserIdFromResetToken(request.getToken());
            String jti = jwtTokenUtil.getJtiFromResetToken(request.getToken());

            if (usedPasswordResetTokenRepository.existsById(jti)) {
                return ResponseEntity.badRequest().body("Invalid or expired reset token.");
            }

            // Find the user by email
            User user = userService.findUserByEmail(email);

            // Update the password
            userService.updatePassword(user.getId(), request.getNewPassword());

            usedPasswordResetTokenRepository.save(new UsedPasswordResetToken(jti));

            return ResponseEntity.ok().body("Password has been reset successfully.");

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid or expired reset token.");
        }
    }
}
