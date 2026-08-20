package com.raitukashtam.auth.jwt;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

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

    public String generateAccessToken(String subject, List<String> roles, String platformRole, String tenantCode) {
        return getLibraryUtil().generateAccessToken(subject, roles, platformRole, tenantCode);
    }

    public String generateRefreshToken(String subject, List<String> roles, String platformRole, String tenantCode) {
        return getLibraryUtil().generateRefreshToken(subject, roles, platformRole, tenantCode);
    }

    public DecodedJWT validateToken(String token) {
        return getLibraryUtil().validateToken(token);
    }

    public DecodedJWT validateAccessToken(String token) {
        return getLibraryUtil().validateAccessToken(token);
    }

    public DecodedJWT validateRefreshToken(String token) {
        return getLibraryUtil().validateRefreshToken(token);
    }

    public String generatePasswordResetToken(String email) {
        return getLibraryUtil().generatePasswordResetToken(email);
    }

    public String getUserIdFromResetToken(String token) {
        return getLibraryUtil().getUserIdFromResetToken(token);
    }
}



