package com.raitukashtam.auth.service;

import com.raitukashtam.auth.config.LoginSecurityConfig;
import com.raitukashtam.auth.entity.LoginAttempt;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.exception.AccountLockedException;
import com.raitukashtam.auth.exception.CaptchaRequiredException;
import com.raitukashtam.auth.exception.TooManyFailedAttemptsException;
import com.raitukashtam.auth.repository.LoginAttemptRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {
    private final LoginAttemptRepository loginAttemptRepository;
    private final LoginSecurityConfig securityConfig;
    private final UserService userService;
    private final RecaptchaService recaptchaService;
    
    @Getter
    @Value("${google.recaptcha.site-key}")
    private String recaptchaSiteKey;

    @Transactional
    public void loginSucceeded(String email, HttpServletRequest request) {
        User user = userService.findUserByEmail(email);
        LoginAttempt attempt = LoginAttempt.success(
            user,
            getClientIP(request),
            request.getHeader("User-Agent")
        );
        loginAttemptRepository.save(attempt);
    }

    @Transactional
    public void loginFailed(String email, String failureReason, HttpServletRequest request) {
        User user = userService.findUserByEmail(email);
        LoginAttempt attempt = LoginAttempt.failure(
            user,
            getClientIP(request),
            request.getHeader("User-Agent"),
            failureReason
        );
        loginAttemptRepository.save(attempt);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> checkLoginAttempts(String email, String recaptchaToken) {
        User user = userService.findUserByEmail(email);
        // Check for too many failed attempts
        Instant windowStart = Instant.now().minus(securityConfig.getFailureWindow());
        int failedAttempts = loginAttemptRepository.countFailedAttemptsSince(user, windowStart);

        // Once failures within the window reach maxAttempts, hold a firmer lock for
        // lockoutDuration from the last failure (independent of the rolling failureWindow).
        if (failedAttempts >= securityConfig.getMaxAttempts()) {
            loginAttemptRepository.findFirstByUserOrderByAttemptTimeDesc(user).ifPresent(lastAttempt -> {
                if (!lastAttempt.isSuccessful()) {
                    Instant unlockTime = lastAttempt.getAttemptTime().plus(securityConfig.getLockoutDuration());
                    if (Instant.now().isBefore(unlockTime)) {
                        throw new AccountLockedException("Account is locked. Please try again after " + unlockTime);
                    }
                }
            });
        }

        // If this is the 4th or later attempt, require reCAPTCHA
        if (failedAttempts >= securityConfig.getCaptchaRequiredAfterAttempts()) {
            if (recaptchaToken == null || recaptchaToken.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("requiresCaptcha", true);
                response.put("siteKey", getRecaptchaSiteKey());
                response.put("message", "Please complete the reCAPTCHA verification");
                return response;
            }
            
            // Verify reCAPTCHA token
            if (!recaptchaService.verifyRecaptcha(recaptchaToken)) {
                throw new CaptchaRequiredException("Invalid reCAPTCHA verification. Please try again.");
            }
        }
        
        if (failedAttempts >= securityConfig.getMaxAttempts()) {
            throw new TooManyFailedAttemptsException("Too many failed login attempts. Please try again later.");
        }
        
        return null;
    }

    public boolean isCaptchaRequired(User user) {
        Instant windowStart = Instant.now().minus(securityConfig.getFailureWindow());
        int failedAttempts = loginAttemptRepository.countFailedAttemptsSince(user, windowStart);
        return failedAttempts >= securityConfig.getCaptchaRequiredAfterAttempts();
    }
    
    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null) {
            return xfHeader.split(",")[0];
        }
        return request.getRemoteAddr();
    }
}
