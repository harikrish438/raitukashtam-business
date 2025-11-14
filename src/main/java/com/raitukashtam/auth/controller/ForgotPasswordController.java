package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.dto.ForgotPasswordRequest;
import com.raitukashtam.auth.dto.ResetPasswordRequest;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.jwt.JwtTokenUtil;
import com.raitukashtam.auth.service.EmailService;
import com.raitukashtam.auth.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ForgotPasswordController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    
    @Autowired
    private EmailService emailService;

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
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
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            // Validate the token and get the email
            String email = jwtTokenUtil.getUserIdFromResetToken(request.getToken());
            
            // Find the user by email
            User user = userService.findUserByEmail(email);
            
            // Update the password
            userService.updatePassword(user.getId(), request.getNewPassword());
            
            return ResponseEntity.ok().body("Password has been reset successfully.");
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid or expired reset token.");
        }
    }
}
