package com.raitukashtam.auth.service;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.raitukashtam.auth.entity.RefreshToken;
import com.raitukashtam.auth.entity.RevokedToken;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.exception.AuthenticationException;
import com.raitukashtam.auth.jwt.JwtTokenUtil;
import com.raitukashtam.auth.repository.RefreshTokenRepository;
import com.raitukashtam.auth.repository.RevokedTokenRepository;
import com.raitukashtam.auth.util.TokenHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class TokenService {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private RevokedTokenRepository revokedTokenRepository;
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private UserService userService;

    @Transactional(noRollbackFor = AuthenticationException.class)
    public Map<String, String> rotateRefreshToken(String presentedRawToken) {
        DecodedJWT presented;
        try {
            presented = jwtTokenUtil.validateRefreshToken(presentedRawToken);
        } catch (JWTVerificationException e) {
            throw new AuthenticationException("Invalid refresh token");
        }

        String presentedHash = TokenHasher.sha256Hex(presentedRawToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(presentedHash)
                .orElseThrow(() -> new AuthenticationException("Invalid refresh token"));

        if (stored.isRevoked()) {
            // Presented token was already rotated away once before -- reuse of a
            // retired token is a theft signal. Kill the whole rotation chain.
            refreshTokenRepository.revokeAllByFamilyId(stored.getFamilyId());
            throw new AuthenticationException("Refresh token reuse detected; session revoked");
        }
        if (stored.getExpiryTime().isBefore(Instant.now())) {
            throw new AuthenticationException("Refresh token expired");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = userService.findUserByEmail(stored.getUsername());
        String newRefreshRaw = jwtTokenUtil.generateRefreshToken(user.getEmail(), user.getRole().name(), user.getTenant().getCode());
        String newAccessRaw = jwtTokenUtil.generateAccessToken(user.getEmail(), user.getRole().name(), user.getTenant().getCode());
        DecodedJWT newRefreshDecoded = jwtTokenUtil.validateRefreshToken(newRefreshRaw);

        RefreshToken newRow = new RefreshToken();
        newRow.setUsername(user.getEmail());
        newRow.setRole(user.getRole().name());
        newRow.setTokenHash(TokenHasher.sha256Hex(newRefreshRaw));
        newRow.setFamilyId(stored.getFamilyId());
        newRow.setExpiryTime(newRefreshDecoded.getExpiresAt().toInstant());
        refreshTokenRepository.save(newRow);

        return Map.of(
                "access_token", newAccessRaw,
                "refresh_token", newRefreshRaw
        );
    }

    public void revokeAccessToken(String jti, Instant expiresAt) {
        // store jti in revoked table so that JWTs with this jti are rejected until expiry
        revokedTokenRepository.save(new RevokedToken(jti, expiresAt));
    }

    public boolean isAccessTokenRevoked(String jti) {
        return revokedTokenRepository.existsById(jti);
    }
}
