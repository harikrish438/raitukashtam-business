package com.raitukashtam.auth.jwt;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    public String generateAccessToken(String username, String role, String tenantCode) {
        return getLibraryUtil().generateAccessToken(username, role, tenantCode);
    }

    public String generateRefreshToken(String username, String role, String tenantCode) {
        return getLibraryUtil().generateRefreshToken(username, role, tenantCode);
    }

    public DecodedJWT validateToken(String token) {
        return getLibraryUtil().validateToken(token);
    }

    public String generatePasswordResetToken(String email) {
        return getLibraryUtil().generatePasswordResetToken(email);
    }

    public String getUserIdFromResetToken(String token) {
        return getLibraryUtil().getUserIdFromResetToken(token);
    }
}



