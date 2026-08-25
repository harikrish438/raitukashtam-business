package com.raitukashtam.auth.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

/**
 * Password-reset tokens only -- access/refresh token issuance moved to
 * Spring Authorization Server in Phase 4a/4b. This is a separate,
 * untouched mechanism (not part of the retired access/refresh stack),
 * still HMAC256-signed directly via auth0's java-jwt (formerly delegated
 * to jwt-library, which this repo no longer has any use for).
 */
@Component
public class JwtTokenUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.access-expiration}")
    private long accessExpiration;

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
        return verifyResetToken(token).getSubject();
    }

    public String getJtiFromResetToken(String token) {
        return verifyResetToken(token).getId();
    }

    private DecodedJWT verifyResetToken(String token) {
        DecodedJWT decodedJWT = JWT.require(Algorithm.HMAC256(secret))
                .withIssuer(issuer)
                .build()
                .verify(token);

        if (!"password_reset".equals(decodedJWT.getClaim("type").asString())) {
            throw new JWTVerificationException("Invalid token type");
        }

        return decodedJWT;
    }
}
