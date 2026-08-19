package com.raitukashtam.auth.service;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.raitukashtam.auth.entity.Identity;
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
import java.util.UUID;

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

    /**
     * Mints a fresh access+refresh token pair for the user, starting a new
     * rotation family. Used for login and Google sign-in.
     */
    @Transactional
    public Map<String, String> issueTokenPair(User user) {
        return issueTokenPair(user, UUID.randomUUID());
    }

    private Map<String, String> issueTokenPair(User user, UUID familyId) {
        Identity identity = user.getIdentity();
        String subject = identity.getId().toString();
        String tenantCode = user.getTenant() != null ? user.getTenant().getCode() : null;

        String accessToken = jwtTokenUtil.generateAccessToken(subject, user.getRole().name(), tenantCode);
        String refreshToken = jwtTokenUtil.generateRefreshToken(subject, user.getRole().name(), tenantCode);
        DecodedJWT refreshDecoded = jwtTokenUtil.validateRefreshToken(refreshToken);

        RefreshToken rt = new RefreshToken();
        rt.setIdentityId(identity.getId());
        rt.setRole(user.getRole().name());
        rt.setTokenHash(TokenHasher.sha256Hex(refreshToken));
        rt.setFamilyId(familyId);
        rt.setExpiryTime(refreshDecoded.getExpiresAt().toInstant());
        refreshTokenRepository.save(rt);

        return Map.of(
                "access_token", accessToken,
                "refresh_token", refreshToken
        );
    }

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

        User user = userService.findByIdentityId(stored.getIdentityId());
        return issueTokenPair(user, stored.getFamilyId());
    }

    public void revokeAccessToken(String jti, Instant expiresAt) {
        // store jti in revoked table so that JWTs with this jti are rejected until expiry
        revokedTokenRepository.save(new RevokedToken(jti, expiresAt));
    }

    public boolean isAccessTokenRevoked(String jti) {
        return revokedTokenRepository.existsById(jti);
    }
}
