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
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(columnDefinition = "UUID")
    private String id; // opaque token id (UUID string)

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private Instant expiryTime;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    public RefreshToken() {}

    public RefreshToken(String username, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.expiryTime = expiresAt;
    }
}

