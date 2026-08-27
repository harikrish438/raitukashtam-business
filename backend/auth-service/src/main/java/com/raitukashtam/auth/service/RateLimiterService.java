package com.raitukashtam.auth.service;

import com.raitukashtam.auth.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed fixed-window rate limiter (INCR + EXPIRE on the first hit
 * in a window) for the unauthenticated, abuse-prone endpoints that have no
 * other protection: OTP generation/verification, forgot/reset-password,
 * Google token verification. Keyed by client IP -- doesn't stop a
 * distributed attack spread across many source IPs at one victim
 * (phone/email), which would need a second, target-keyed layer; IP-based
 * is the accepted scope for this pass, matching what a reverse proxy/WAF
 * would normally provide as the first layer in front of a service like
 * this.
 */
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;

    public void checkLimit(String bucket, HttpServletRequest request, int maxAttempts, Duration window) {
        String key = KEY_PREFIX + bucket + ":" + clientIp(request);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
        if (count != null && count > maxAttempts) {
            throw new RateLimitExceededException("Too many requests -- please try again later.");
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
