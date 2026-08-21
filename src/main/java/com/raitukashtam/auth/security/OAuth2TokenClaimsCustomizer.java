package com.raitukashtam.auth.security;

import com.raitukashtam.auth.entity.Client;
import com.raitukashtam.auth.entity.Identity;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.repository.ClientRepository;
import com.raitukashtam.auth.repository.IdentityRepository;
import com.raitukashtam.auth.repository.ProductMembershipRepository;
import com.raitukashtam.auth.repository.UserRepository;
import com.raitukashtam.auth.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Adds the roles/platform_role claims Phase 3 defined, for user
 * (Authorization Code) logins only -- client_credentials tokens have no
 * Identity behind them and correctly get none of these, matching what was
 * verified live in Phase 4a. Roles are scoped to the requesting client's
 * own product (via Client.product), tightening what TokenService used to
 * do with a single hardcoded default product -- not possible before Phase
 * 4a introduced the Client entity.
 *
 * Re-fetches Identity/User fresh by the principal's identity UUID (rather
 * than reading a custom principal object) -- see
 * IdentityAuthenticationProvider for why the authenticated principal is a
 * plain UsernamePasswordAuthenticationToken. Runs as a servlet filter
 * (OAuth2TokenEndpointFilter), before any Spring MVC open-session-in-view
 * scope, so this needs its own transaction to safely touch lazy
 * associations (identity.getIdentity()).
 */
@Component
public class OAuth2TokenClaimsCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    @Autowired
    private IdentityRepository identityRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private ProductMembershipRepository productMembershipRepository;
    @Autowired
    private RoleService roleService;

    @Override
    @Transactional(readOnly = true)
    public void customize(JwtEncodingContext context) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }
        if (!(context.getPrincipal() instanceof UsernamePasswordAuthenticationToken)) {
            return; // client_credentials: no resource-owner identity behind the token
        }

        UUID identityId;
        try {
            identityId = UUID.fromString(context.getPrincipal().getName());
        } catch (IllegalArgumentException e) {
            return;
        }

        Identity identity = identityRepository.findById(identityId).orElse(null);
        User user = userRepository.findByIdentity_Id(identityId).orElse(null);
        if (identity == null || user == null) {
            return;
        }

        Client client = clientRepository.findByClientId(context.getRegisteredClient().getClientId()).orElse(null);
        List<String> roles = List.of();
        if (client != null) {
            roles = productMembershipRepository
                    .findByIdentity_IdAndProduct_Code(identityId, client.getProduct().getCode())
                    .map(roleService::getRoleCodes)
                    .orElse(List.of());
        }

        context.getClaims().claim("roles", roles);
        if (identity.isPlatformAdmin()) {
            context.getClaims().claim("platform_role", "PLATFORM_ADMIN");
        }
    }
}
