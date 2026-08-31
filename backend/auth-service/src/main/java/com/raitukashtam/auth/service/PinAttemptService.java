package com.raitukashtam.auth.service;

import com.raitukashtam.auth.config.PinSecurityConfig;
import com.raitukashtam.auth.exception.TooManyFailedAttemptsException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Per-device failed-PIN lockout, Redis-backed like OTPService/
 * RateLimiterService (no new table). Deliberately separate from
 * RateLimiterService: that one increments on every call regardless of
 * outcome (a flat request quota), while this needs "N *consecutive
 * failures*, reset on success" -- a wrong PIN counts against the device,
 * a right one clears the slate.
 */
@Service
@RequiredArgsConstructor
public class PinAttemptService {

    private static final String KEY_PREFIX = "pin-attempts:";

    private final StringRedisTemplate redisTemplate;
    private final PinSecurityConfig pinSecurityConfig;

    public void checkNotLocked(String deviceId) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + deviceId);
        int failures = value == null ? 0 : Integer.parseInt(value);
        if (failures >= pinSecurityConfig.getMaxAttempts()) {
            throw new TooManyFailedAttemptsException("Too many failed PIN attempts -- please try again later.");
        }
    }

    public void recordFailure(String deviceId) {
        String key = KEY_PREFIX + deviceId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, pinSecurityConfig.getLockoutDuration());
        }
    }

    public void recordSuccess(String deviceId) {
        redisTemplate.delete(KEY_PREFIX + deviceId);
    }
}
