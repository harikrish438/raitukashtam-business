package com.raitukashtam.auth.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

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

    public String generateAccessToken(String username, String role, String tenantCode) {
        return JWT.create()
                .withIssuer(issuer)
                .withSubject(username)
                .withClaim("role", role)
                .withClaim("tenant_code", tenantCode)
                .withJWTId(UUID.randomUUID().toString())  // Add JWT ID
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + accessExpiration))
                .sign(Algorithm.HMAC256(secret));
    }

    public String generateRefreshToken(String username, String role, String tenantCode) {
        return JWT.create()
                .withIssuer(issuer)
                .withSubject(username)
                .withClaim("type", "refresh")
                .withClaim("role", role)
                .withClaim("tenant_code", tenantCode)
                .withJWTId(UUID.randomUUID().toString())
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + accessExpiration))
                .sign(Algorithm.HMAC256(secret));
    }

    public DecodedJWT validateToken(String token) {
        JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secret))
                .withIssuer(issuer)
                .build();
        return verifier.verify(token);
    }
    
    public String generatePasswordResetToken(String email) {
        return JWT.create()
                .withIssuer(issuer)
                .withSubject(email)
                .withClaim("type", "password_reset")
                .withJWTId(UUID.randomUUID().toString())
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + accessExpiration))
                .sign(Algorithm.HMAC256(secret));
    }
    
    public String getUserIdFromResetToken(String token) {
        try {
            DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(secret))
                    .withIssuer(issuer)
                    .build()
                    .verify(token);
                    
            // Verify this is a password reset token
            if (!"password_reset".equals(decodedJWT.getClaim("type").asString())) {
                throw new JWTVerificationException("Invalid token type");
            }
            
            return decodedJWT.getSubject();
        } catch (JWTVerificationException e) {
            throw new JWTVerificationException("Invalid or expired reset token");
        }
    }
}



