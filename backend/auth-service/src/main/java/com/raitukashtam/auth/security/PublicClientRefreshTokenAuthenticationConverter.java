package com.raitukashtam.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import java.util.Collections;

/**
 * Authenticates a public (no-secret, PKCE-only) client on the refresh_token grant using just
 * its client_id. Spring Authorization Server's own {@code PublicClientAuthenticationConverter}
 * only ever matches the authorization_code grant -- it requires a {@code code_verifier}, which
 * doesn't exist on a refresh grant -- so a NONE-auth client's {@code POST /oauth2/token}
 * refresh_token requests would otherwise never authenticate at all (every registered converter
 * returns null, the request falls through as anonymous, and the shared filter chain's
 * {@code LoginUrlAuthenticationEntryPoint} redirects it to {@code /login}).
 * <p>
 * Pairs with {@link PublicClientRefreshTokenAuthenticationProvider}. See
 * {@code AuthorizationServerConfig.tokenGenerator()}'s javadoc for why issuing refresh tokens
 * to this client is safe -- this is the other half of that fix: without it, a refresh token
 * that's issued can never actually be redeemed.
 */
public final class PublicClientRefreshTokenAuthenticationConverter implements AuthenticationConverter {

    @Nullable
    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        if (!AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
            return null;
        }

        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        if (!StringUtils.hasText(clientId)) {
            return null; // let other converters (client_secret_basic, etc.) have a chance
        }
        // A client presenting a secret isn't a public client -- leave it to the other converters.
        if (request.getHeader("Authorization") != null
                || StringUtils.hasText(request.getParameter(OAuth2ParameterNames.CLIENT_SECRET))) {
            return null;
        }

        return new OAuth2ClientAuthenticationToken(clientId, ClientAuthenticationMethod.NONE, null, Collections.emptyMap());
    }
}
