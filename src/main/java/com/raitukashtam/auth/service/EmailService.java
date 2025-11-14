package com.raitukashtam.auth.service;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetToken);
}
