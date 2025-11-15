package com.raitukashtam.auth.service;

import com.raitukashtam.auth.config.LoginSecurityConfig;
import com.raitukashtam.auth.entity.LoginAttempt;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.exception.AccountLockedException;
import com.raitukashtam.auth.exception.TooManyFailedAttemptsException;
import com.raitukashtam.auth.repository.LoginAttemptRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {
    private final LoginAttemptRepository loginAttemptRepository;
    private final LoginSecurityConfig securityConfig;
    private final UserService userService;

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
    public void checkLoginAttempts(String email) {
        User user = userService.findUserByEmail(email);
        
        // Check if account is locked
        loginAttemptRepository.findLastLoginAttempt(user).ifPresent(lastAttempt -> {
            if (!lastAttempt.isSuccessful() && isAccountLocked(user)) {
                Instant unlockTime = lastAttempt.getAttemptTime().plus(securityConfig.getLockoutDuration());
                throw new AccountLockedException("Account is locked. Please try again after " + unlockTime);
            }
        });

        // Check for too many failed attempts
        Instant windowStart = Instant.now().minus(securityConfig.getFailureWindow());
        int failedAttempts = loginAttemptRepository.countFailedAttemptsSince(user, windowStart);
        
        if (failedAttempts >= securityConfig.getMaxAttempts()) {
            throw new TooManyFailedAttemptsException("Too many failed login attempts. Please try again later.");
        }
    }

    private boolean isAccountLocked(User user) {
        return loginAttemptRepository.findLastLoginAttempt(user)
            .map(attempt -> {
                if (attempt.isSuccessful()) return false;
                
                Instant lockEnd = attempt.getAttemptTime().plus(securityConfig.getLockoutDuration());
                return Instant.now().isBefore(lockEnd);
            })
            .orElse(false);
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null) {
            return xfHeader.split(",")[0];
        }
        return request.getRemoteAddr();
    }
}
