package com.raitukashtam.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
public class LoginAttempt extends BaseEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private String userAgent;

    @Column(nullable = false)
    private boolean successful;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant attemptTime;

    @Column
    private String failureReason;

    protected LoginAttempt() {}

    public static LoginAttempt success(User user, String ipAddress, String userAgent) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUser(user);
        attempt.setIpAddress(ipAddress);
        attempt.setUserAgent(userAgent);
        attempt.setSuccessful(true);
        return attempt;
    }

    public static LoginAttempt failure(User user, String ipAddress, String userAgent, String failureReason) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUser(user);
        attempt.setIpAddress(ipAddress);
        attempt.setUserAgent(userAgent);
        attempt.setSuccessful(false);
        attempt.setFailureReason(failureReason);
        return attempt;
    }
}
