package com.raitukashtam.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.UUID;

@Setter
@Getter
@Entity
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = true)
    private String role;

    @Column(nullable = false)
    private Instant expiryTime;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    public RefreshToken() {}

    public RefreshToken(String username, String role, Instant expiresAt) {
        //this.id = UUID.randomUUID().toString();
        this.username = username;
        this.role = role;
        this.expiryTime = expiresAt;
    }
}

