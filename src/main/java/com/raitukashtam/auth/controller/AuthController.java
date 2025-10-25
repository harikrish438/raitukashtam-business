package com.raitukashtam.auth.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.raitukashtam.auth.entity.RefreshToken;
import com.raitukashtam.auth.entity.RevokedToken;
import com.raitukashtam.auth.jwt.JwtTokenUtil;
import com.raitukashtam.auth.repository.RefreshTokenRepository;
import com.raitukashtam.auth.repository.TokenBlackListRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
public class AuthController {

    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private RefreshTokenRepository refreshRepo;

    @Autowired
    private TokenBlackListRepository blacklistRepo;

    @PostMapping("/token")
    public Map<String, String> login(@RequestParam String username,
                                     @RequestParam String password) {
        if ("alice".equals(username) && "password".equals(password)) {
            String accessToken = jwtTokenUtil.generateAccessToken(username, "ROLE_USER");
            String refreshToken = jwtTokenUtil.generateRefreshToken(username);

            RefreshToken rt = new RefreshToken();
            rt.setUsername(username);
            rt.setToken(refreshToken);
            rt.setExpiryTime(Instant.now().plusMillis(7 * 24 * 60 * 60 * 1000));
            refreshRepo.save(rt);

            return Map.of(
                    "access_token", accessToken,
                    "refresh_token", refreshToken
            );
        }
        return Map.of("error", "Invalid credentials");
    }

    @PostMapping("/refresh")
    public Map<String, String> refresh(@RequestParam String refreshToken) {
        var storedToken = refreshRepo.findByToken(refreshToken)
                .filter(t -> !t.isRevoked() && t.getExpiryTime().isAfter(Instant.now()))
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        DecodedJWT jwt = jwtTokenUtil.validateToken(refreshToken);
        String username = jwt.getSubject();

        String newAccessToken = jwtTokenUtil.generateAccessToken(username, "ROLE_USER");

        return Map.of("access_token", newAccessToken);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        DecodedJWT jwt = jwtTokenUtil.validateToken(token);

        RevokedToken revoked = new RevokedToken();
        revoked.setJti(jwt.getId());
        revoked.setExpiresAt(jwt.getExpiresAt().toInstant());
        blacklistRepo.save(revoked);

        refreshRepo.deleteByUsername(jwt.getSubject());

        return Map.of("message", "Logged out successfully");
    }
}


