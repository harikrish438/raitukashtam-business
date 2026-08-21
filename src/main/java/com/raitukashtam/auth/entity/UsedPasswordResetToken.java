package com.raitukashtam.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Marks a password-reset JWT's jti as consumed, so a second use of the
 * same token (e.g. a leaked reset link replayed within its TTL) is
 * rejected. See ForgotPasswordController.resetPassword.
 */
@Setter
@Getter
@Entity
@Table(name = "used_password_reset_token")
public class UsedPasswordResetToken {
    @Id
    @Column(name = "jti", length = 255)
    private String jti;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    public UsedPasswordResetToken() {}

    public UsedPasswordResetToken(String jti) {
        this.jti = jti;
        this.usedAt = Instant.now();
    }
}
