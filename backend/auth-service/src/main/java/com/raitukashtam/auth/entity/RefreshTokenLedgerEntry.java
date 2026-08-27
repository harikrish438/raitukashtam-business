package com.raitukashtam.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Archives a refresh token's hash the moment Spring Authorization Server
 * rotates it away (overwrites it in oauth2_authorization), so a later
 * replay of that exact value can be recognized as reuse instead of just
 * "not found". See ReuseDetectingAuthorizationService.
 */
@Setter
@Getter
@Entity
@Table(name = "refresh_token_ledger")
public class RefreshTokenLedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "authorization_id", nullable = false, length = 100)
    private String authorizationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public RefreshTokenLedgerEntry() {}

    public RefreshTokenLedgerEntry(String tokenHash, String authorizationId) {
        this.tokenHash = tokenHash;
        this.authorizationId = authorizationId;
        this.createdAt = Instant.now();
    }
}
