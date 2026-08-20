package com.raitukashtam.auth.security;

import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.exception.AccountLockedException;
import com.raitukashtam.auth.exception.CaptchaRequiredException;
import com.raitukashtam.auth.exception.TooManyFailedAttemptsException;
import com.raitukashtam.auth.service.LoginAttemptService;
import com.raitukashtam.auth.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Bridges the login-page flow to exactly the same checks
 * AuthController.login() used to do (hard-lock -> captcha/rolling-lockout
 * -> credential match), reusing UserService/LoginAttemptService as-is
 * rather than re-deriving the logic.
 */
@Component
public class IdentityAuthenticationProvider implements AuthenticationProvider {

    @Autowired
    private UserService userService;
    @Autowired
    private LoginAttemptService loginAttemptService;

    @Override
    @Transactional
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        CaptchaAwareAuthenticationToken authRequest = (CaptchaAwareAuthenticationToken) authentication;
        String username = authRequest.getUsername();

        User user;
        try {
            user = userService.findUserByEmail(username);
        } catch (UsernameNotFoundException e) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // Admin hard-lock takes precedence over the rolling captcha/lockout
        // gate below, same ordering as the pre-4b AuthController.login().
        if (user.isLocked()) {
            throw new LockedException("Account is locked. Please contact support.");
        }

        Map<String, Object> captchaResponse;
        try {
            captchaResponse = loginAttemptService.checkLoginAttempts(username, authRequest.getRecaptchaToken());
        } catch (AccountLockedException | TooManyFailedAttemptsException e) {
            throw new LockedException(e.getMessage());
        } catch (CaptchaRequiredException e) {
            throw new BadCredentialsException(e.getMessage());
        }
        if (captchaResponse != null) {
            String siteKey = (String) captchaResponse.get("siteKey");
            throw new CaptchaRequiredAuthenticationException("Please complete the reCAPTCHA verification", siteKey);
        }

        try {
            userService.authenticate(username, (String) authRequest.getCredentials());
        } catch (AccountLockedException e) {
            throw new LockedException(e.getMessage());
        } catch (com.raitukashtam.auth.exception.AuthenticationException e) {
            throw new BadCredentialsException("Invalid email or password");
        }

        // Principal is the identity UUID string (matches the sub format
        // Phase 2/3 established) via a plain UsernamePasswordAuthenticationToken
        // -- Spring AS serializes this into oauth2_authorization.attributes
        // between /oauth2/authorize and the code exchange at /oauth2/token,
        // and only Spring's own well-known Authentication types are on the
        // Jackson allowlist for that round trip (see CaptchaAwareAuthenticationToken).
        // OAuth2TokenClaimsCustomizer re-fetches Identity/User fresh from this
        // UUID at token-issuance time rather than smuggling that data through
        // a custom, hard-to-serialize principal object.
        return new UsernamePasswordAuthenticationToken(
                user.getIdentity().getId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return CaptchaAwareAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
