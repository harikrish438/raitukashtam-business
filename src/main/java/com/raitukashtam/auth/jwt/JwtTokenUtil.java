package com.raitukashtam.auth.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Password-reset tokens only -- access/refresh token issuance moved to
 * Spring Authorization Server in Phase 4a/4b. This is a separate,
 * untouched mechanism (not part of the retired access/refresh stack),
 * still backed by jwt-library's HMAC signing.
 */
@Component
public class JwtTokenUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    private com.raitukashtam.jwt.JwtTokenUtil getLibraryUtil() {
        return new com.raitukashtam.jwt.JwtTokenUtil(secret, issuer, accessExpiration, refreshExpiration);
    }

    public String generatePasswordResetToken(String email) {
        return getLibraryUtil().generatePasswordResetToken(email);
    }

    public String getUserIdFromResetToken(String token) {
        return getLibraryUtil().getUserIdFromResetToken(token);
    }
}
