package com.raitukashtam.auth.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

/**
 * Unauthenticated carrier only: username/password/recaptchaToken from the
 * login form, through to IdentityAuthenticationProvider.
 * IdentityAuthenticationProvider returns a plain
 * UsernamePasswordAuthenticationToken on success (not this class) -- that
 * type is on Spring Security's own Jackson allowlist, needed because
 * Spring Authorization Server serializes the resource owner's
 * Authentication into oauth2_authorization.attributes between
 * /oauth2/authorize and the code exchange at /oauth2/token; a custom
 * Authentication subtype would need its own Jackson mixins registered to
 * survive that round trip, which a plain UsernamePasswordAuthenticationToken
 * avoids needing entirely.
 */
public class CaptchaAwareAuthenticationToken extends AbstractAuthenticationToken {

    private final String username;
    private final String password;
    private final String recaptchaToken;

    public CaptchaAwareAuthenticationToken(String username, String password, String recaptchaToken) {
        super(Collections.emptyList());
        this.username = username;
        this.password = password;
        this.recaptchaToken = recaptchaToken;
        setAuthenticated(false);
    }

    @Override
    public Object getCredentials() {
        return password;
    }

    @Override
    public Object getPrincipal() {
        return username;
    }

    public String getUsername() {
        return username;
    }

    public String getRecaptchaToken() {
        return recaptchaToken;
    }
}
