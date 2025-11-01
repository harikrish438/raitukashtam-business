package com.raitukashtam.auth.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.raitukashtam.auth.entity.RefreshToken;
import com.raitukashtam.auth.entity.RevokedToken;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.jwt.JwtTokenUtil;
import com.raitukashtam.auth.repository.RefreshTokenRepository;
import com.raitukashtam.auth.repository.TokenBlackListRepository;
import com.raitukashtam.auth.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
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

    @Autowired
    private UserService userService;

    @PostMapping("/token")
    public Map<String, String> login(@RequestParam String username,
                                     @RequestParam String password) {
        // Authenticate user
        User user = userService.authenticate(username, password);
        String accessToken = jwtTokenUtil.generateAccessToken(username, user.getRole().name(), user.getTenant().getCode());
        String refreshToken = jwtTokenUtil.generateRefreshToken(username, user.getRole().name(), user.getTenant().getCode());

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

    @PostMapping("/refresh")
    public Map<String, String> refresh(@RequestHeader("Authorization") String refreshToken) {
        refreshToken = refreshToken.substring(7);
        RefreshToken storedToken = refreshRepo.findByToken(refreshToken)
                .filter(t -> !t.isRevoked() && t.getExpiryTime().isAfter(Instant.now()))
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        DecodedJWT jwt = jwtTokenUtil.validateToken(storedToken.getToken());
        String username = jwt.getSubject();

        User user = userService.findUserByEmail(username);

        String newAccessToken = jwtTokenUtil.generateAccessToken(user.getEmail(), user.getRole().name(), user.getTenant().getCode());

        return Map.of("access_token", newAccessToken);
    }

    @PostMapping("/invalidateToken")
    public Map<String, String> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        DecodedJWT jwt = jwtTokenUtil.validateToken(token);

        RevokedToken revoked = new RevokedToken();
        revoked.setJti(jwt.getId());
        revoked.setExpiresAt(jwt.getExpiresAt().toInstant());
        blacklistRepo.save(revoked);

        userService.deleteByUsername(jwt.getSubject());

        return Map.of("message", "Access token invalidated");
    }
}


