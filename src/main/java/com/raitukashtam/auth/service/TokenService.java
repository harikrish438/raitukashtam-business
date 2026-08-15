package com.raitukashtam.auth.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.raitukashtam.auth.entity.RefreshToken;
import com.raitukashtam.auth.entity.RevokedToken;
import com.raitukashtam.auth.repository.RefreshTokenRepository;
import com.raitukashtam.auth.repository.RevokedTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

@Service
public class TokenService {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private RevokedTokenRepository revokedTokenRepository;
    private final Algorithm hmac;
    private final String issuer;
    private final long accessTokenExpMs;
    private final long refreshTokenExpMs;

    @Value("${jwt.secret:my-super-secret-key-please-change-this-in-production}")
    private String secret;
    
    @Value("${jwt.issuer:raitukashtam-auth-service}")
    private String jwtIssuer;
    
    @Value("${jwt.access-token-exp-ms:900000}")
    private long accessTokenExpirationMs;
    
    @Value("${jwt.refresh-token-exp-ms:604800000}")
    private long refreshTokenExpirationMs;

    public TokenService(@Value("${jwt.secret:my-super-secret-key-please-change-this-in-production}") String secret,
                       @Value("${jwt.issuer:raitukashtam-auth-service}") String issuer,
                       @Value("${jwt.access-token-exp-ms:900000}") long accessTokenExpMs,
                       @Value("${jwt.refresh-token-exp-ms:604800000}") long refreshTokenExpMs) {
        this.hmac = Algorithm.HMAC256(secret);
        this.issuer = issuer != null ? issuer : "raitukashtam-auth-service";
        this.accessTokenExpMs = accessTokenExpMs > 0 ? accessTokenExpMs : 900000; // 15 min default
        this.refreshTokenExpMs = refreshTokenExpMs > 0 ? refreshTokenExpMs : 604800000; // 7 days default
    }

    public Map<String, String> createAccessAndRefreshTokens(String username, String role) {
        Instant now = Instant.now();
        Instant accessExp = now.plusMillis(accessTokenExpMs);

        String jti = java.util.UUID.randomUUID().toString();

        String accessToken = JWT.create()
                .withIssuer(issuer)
                .withSubject(username)
                .withClaim("role", role)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(accessExp))
                .withJWTId(jti)
                .sign(hmac);

        Instant refreshExp = now.plusMillis(refreshTokenExpMs);
        RefreshToken refreshToken = new RefreshToken(username, role, refreshExp);
        refreshTokenRepository.save(refreshToken);

        return Map.of(
                "access_token", accessToken,
                "refresh_token", String.valueOf(refreshToken.getId()),
                "token_type", "Bearer",
                "expires_in", String.valueOf(accessTokenExpMs / 1000)
        );
    }

    public Optional<Map<String, String>> refresh(String refreshTokenId) {
        Optional<RefreshToken> dbTokenOpt = refreshTokenRepository.findByToken(refreshTokenId);
        if (dbTokenOpt.isEmpty()) return Optional.empty();

        RefreshToken dbToken = dbTokenOpt.get();
        if (dbToken.getExpiryTime().isBefore(Instant.now())) {
            // expired -> remove and deny
            refreshTokenRepository.delete(dbToken);
            return Optional.empty();
        }

        // rotate refresh token: mark old revoked + issue new one
        dbToken.setRevoked(true);
        refreshTokenRepository.save(dbToken);

        // new refresh token
        RefreshToken newRefresh = new RefreshToken(dbToken.getUsername(), dbToken.getRole(), Instant.now().plusMillis(refreshTokenExpMs));
        refreshTokenRepository.save(newRefresh);

        // new access token
        String newAccessTokenJti = java.util.UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant accessExp = now.plusMillis(accessTokenExpMs);

        String accessToken = JWT.create()
                .withIssuer(issuer)
                .withSubject(dbToken.getUsername())
                .withClaim("role", dbToken.getRole())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(accessExp))
                .withJWTId(newAccessTokenJti)
                .sign(hmac);

        return Optional.of(Map.of(
                "access_token", accessToken,
                "refresh_token", String.valueOf(newRefresh.getId()),
                "token_type", "Bearer",
                "expires_in", String.valueOf(accessTokenExpMs / 1000)
        ));
    }

    public void revokeAccessToken(String jti, Instant expiresAt) {
        // store jti in revoked table so that JWTs with this jti are rejected until expiry
        revokedTokenRepository.save(new RevokedToken(jti, expiresAt));
    }

    public void revokeRefreshToken(Long refreshTokenId) {
        refreshTokenRepository.findById(refreshTokenId).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }

    public boolean isAccessTokenRevoked(String jti) {
        return revokedTokenRepository.existsById(jti);
    }
}

