package com.raitukashtam.auth.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.raitukashtam.auth.entity.RevokedToken;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.exception.AccountLockedException;
import com.raitukashtam.auth.exception.AuthenticationException;
import com.raitukashtam.auth.exception.CaptchaRequiredException;
import com.raitukashtam.auth.exception.TooManyFailedAttemptsException;
import com.raitukashtam.auth.jwt.JwtTokenUtil;
import com.raitukashtam.auth.repository.TokenBlackListRepository;
import com.raitukashtam.auth.request.LoginRequest;
import com.raitukashtam.auth.service.LoginAttemptService;
import com.raitukashtam.auth.service.TokenService;
import com.raitukashtam.auth.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class AuthController {

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private TokenBlackListRepository blacklistRepo;

    @Autowired
    private UserService userService;
    @Autowired
    private LoginAttemptService loginAttemptService;
    @Autowired
    private TokenService tokenService;

    @PostMapping("/token")
    public Object login(
            @RequestBody @Validated LoginRequest loginRequest, HttpServletRequest request) {
        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();
        String recaptchaToken = loginRequest.getRecaptchaToken();
        
        try {
            // Admin hard-lock takes precedence over the rolling captcha/lockout gate below,
            // so a locked account is never masked behind "requiresCaptcha".
            if (userService.findUserByEmail(username).isLocked()) {
                throw new AccountLockedException("Account is locked. Please contact support.");
            }

            // Check if reCAPTCHA verification is needed
            Map<String, Object> captchaResponse = loginAttemptService.checkLoginAttempts(username, recaptchaToken);
            if (captchaResponse != null) {
                return captchaResponse; // Returns reCAPTCHA required response
            }
            
            // Authenticate user
            User user = userService.authenticate(username, password);
            loginAttemptService.loginSucceeded(username, request);

            return tokenService.issueTokenPair(user);
            
        } catch (AuthenticationException e) {
            loginAttemptService.loginFailed(username, "Invalid credentials", request);
            
            // Check if we need to return reCAPTCHA for the next attempt
            User user = userService.findUserByEmail(username);
            if (loginAttemptService.isCaptchaRequired(user)) {
                Map<String, Object> response = new HashMap<>();
                response.put("requiresCaptcha", true);
                response.put("siteKey", loginAttemptService.getRecaptchaSiteKey());
                response.put("message", "Please complete the reCAPTCHA verification");
                return response;
            }
            
            throw e;
            
        } catch (AccountLockedException | TooManyFailedAttemptsException | CaptchaRequiredException e) {
            throw e;
            
        } catch (Exception e) {
            String reason = "Unexpected error: " + e.getMessage();
            // failure_reason is varchar(255) -- an unbounded nested exception
            // message would otherwise blow the column and mask the real error
            // behind a confusing secondary one.
            if (reason.length() > 255) {
                reason = reason.substring(0, 255);
            }
            loginAttemptService.loginFailed(username, reason, request);
            throw e;
        }
    }

    @PostMapping("/refresh")
    public Map<String, String> refresh(@RequestHeader("Authorization") String refreshToken) {
        refreshToken = refreshToken.substring(7);
        return tokenService.rotateRefreshToken(refreshToken);
    }

    @PostMapping("/invalidateToken")
    public Map<String, String> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        DecodedJWT jwt = jwtTokenUtil.validateAccessToken(token);

        RevokedToken revoked = new RevokedToken();
        revoked.setJti(jwt.getId());
        revoked.setExpiresAt(jwt.getExpiresAt().toInstant());
        blacklistRepo.save(revoked);

        userService.deleteRefreshTokensByIdentityId(UUID.fromString(jwt.getSubject()));

        return Map.of("message", "Access token invalidated");
    }
}


