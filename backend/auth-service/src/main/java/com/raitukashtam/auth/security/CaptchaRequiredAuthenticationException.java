package com.raitukashtam.auth.security;

import org.springframework.security.core.AuthenticationException;

/**
 * Thrown when a login attempt has crossed the failed-attempt threshold
 * and must be retried with a reCAPTCHA token -- distinct from a genuine
 * bad-credentials failure so the login page can render the widget
 * immediately without this counting as another failed attempt.
 */
public class CaptchaRequiredAuthenticationException extends AuthenticationException {
    private final String siteKey;

    public CaptchaRequiredAuthenticationException(String message, String siteKey) {
        super(message);
        this.siteKey = siteKey;
    }

    public String getSiteKey() {
        return siteKey;
    }
}
