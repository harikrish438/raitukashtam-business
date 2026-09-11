package com.raitukashtam.auth.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.util.Assert;

/**
 * Pairs with {@link PublicClientRefreshTokenAuthenticationConverter} -- see its javadoc.
 * Deliberately does not attempt any PKCE/code_verifier check (there is no authorization code
 * or code_challenge involved in a refresh grant); proof of possession here is the refresh
 * token value itself, which {@code OAuth2RefreshTokenAuthenticationProvider} validates
 * separately against the stored authorization.
 */
public final class PublicClientRefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-3.2.1";

    private final RegisteredClientRepository registeredClientRepository;

    public PublicClientRefreshTokenAuthenticationProvider(RegisteredClientRepository registeredClientRepository) {
        Assert.notNull(registeredClientRepository, "registeredClientRepository cannot be null");
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2ClientAuthenticationToken clientAuthentication = (OAuth2ClientAuthenticationToken) authentication;
        if (!ClientAuthenticationMethod.NONE.equals(clientAuthentication.getClientAuthenticationMethod())) {
            return null;
        }
        if (clientAuthentication.getPrincipal() instanceof RegisteredClient) {
            return null; // already authenticated -- not our converter's output
        }

        String clientId = clientAuthentication.getPrincipal().toString();
        RegisteredClient registeredClient = this.registeredClientRepository.findByClientId(clientId);
        if (registeredClient == null || !registeredClient.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) {
            throwInvalidClient();
        }

        return new OAuth2ClientAuthenticationToken(registeredClient, ClientAuthenticationMethod.NONE, null);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static void throwInvalidClient() {
        OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT, "Client authentication failed", ERROR_URI);
        throw new OAuth2AuthenticationException(error);
    }
}
