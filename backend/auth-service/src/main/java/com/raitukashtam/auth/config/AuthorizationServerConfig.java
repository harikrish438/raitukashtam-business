package com.raitukashtam.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.raitukashtam.auth.repository.RefreshTokenLedgerRepository;
import com.raitukashtam.auth.security.PublicClientRefreshTokenAuthenticationConverter;
import com.raitukashtam.auth.security.PublicClientRefreshTokenAuthenticationProvider;
import com.raitukashtam.auth.security.ReuseDetectingAuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * Phase 4a: wires up Spring Authorization Server as a second, independent
 * filter chain (@Order(1), matched to /oauth2/** + /.well-known/** by
 * applyDefaultSecurity) alongside the existing SecurityConfig chain
 * (@Order(2)). Only client_credentials is reachable this phase -- no
 * formLogin/login page is configured since that's Phase 4b's concern
 * (Authorization Code + PKCE, which needs a browser-authenticated user).
 */
@Configuration
public class AuthorizationServerConfig {

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${jwt.signing-key.private-key}")
    private String signingKeyPrivateBase64;

    @Value("${jwt.signing-key.public-key}")
    private String signingKeyPublicBase64;

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http, CorsConfigurationSource corsConfigurationSource,
            RegisteredClientRepository registeredClientRepository) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        // Pairs with tokenGenerator() below: that bean makes refresh tokens get issued to
        // public (NONE-auth) clients on the authorization_code grant; this makes those same
        // clients able to actually redeem one on the refresh_token grant. Spring's own
        // PublicClientAuthenticationConverter only ever matches authorization_code+PKCE
        // (see PublicClientRefreshTokenAuthenticationConverter's javadoc for why), so without
        // this a refresh_token request from a NONE-auth client never authenticates at all and
        // 401s via a redirect to /login instead of reaching the token endpoint's grant logic.
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                http.getConfigurer(OAuth2AuthorizationServerConfigurer.class);
        authorizationServerConfigurer.clientAuthentication(clientAuthentication -> clientAuthentication
                .authenticationConverter(new PublicClientRefreshTokenAuthenticationConverter())
                .authenticationProvider(new PublicClientRefreshTokenAuthenticationProvider(registeredClientRepository)));

        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        String issuer = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return AuthorizationServerSettings.builder()
                .issuer(issuer)
                .build();
    }

    /**
     * Persistent keypair, sourced from jwt.signing-key.private-key/public-key
     * (base64 PKCS8/X509 DER) -- Vault-seeded in test/prod, a checked-in
     * dev-only default in application-dev.yml, same wiring pattern as
     * jwt.secret. Must be stable across restarts/replicas: a fresh keypair
     * every boot (the old behavior) invalidated every outstanding token on
     * every restart and would make a second replica sign with a different
     * key than the first validates against. keyIDFromThumbprint() derives
     * a deterministic kid from the key material itself (RFC 7638), so the
     * same key always produces the same kid -- a random kid here would
     * defeat the fix even with the key material itself persisted, since
     * JWKS lookup matches by kid first.
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(signingKeyPrivateBase64)));
        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(signingKeyPublicBase64)));
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyIDFromThumbprint()
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * Overrides Spring Authorization Server's default token generator composition so that
     * public clients (PKCE-only, {@code clientAuthenticationMethod(NONE)} -- every mobile
     * client registered via JpaRegisteredClientRepository, e.g. mycommunity-android) still get
     * a refresh token on the authorization_code grant. Spring's built-in
     * {@code OAuth2RefreshTokenGenerator} deliberately withholds one in exactly that case
     * (see {@code OAuth2RefreshTokenGenerator.isPublicClientForAuthorizationCodeGrant()}) --
     * a reasonable default for a client with no way to prove its identity on refresh, but this
     * deployment already carries the mitigations that make it safe here: refresh tokens are
     * single-use (reuseRefreshTokens(false)) and any replay of a rotated-away token is caught
     * and revokes the whole session (ReuseDetectingAuthorizationService) -- the standard
     * "refresh token rotation" pattern OAuth 2.0 for Native Apps (RFC 8252) recommends for
     * exactly this kind of public client. Without this, a mobile session dies the moment its
     * access token expires (currently 1 hour), forcing OTP/PIN/Google re-login every time.
     * <p>
     * {@link AlwaysIssueOAuth2RefreshTokenGenerator} is a straight copy of Spring's own
     * generator minus that one check. The JWT access-token side is otherwise built exactly the
     * way Spring's own default composition would (same {@link JwtEncoder} derived from the
     * existing {@code jwkSource} bean, same {@link OAuth2TokenClaimsCustomizer} applied) --
     * only the refresh-token half of the delegate list is overridden. Deliberately not
     * replicating Spring's internal {@code DefaultOAuth2TokenCustomizers.jwtCustomizer()} here:
     * it only adds the mTLS {@code cnf} claim and Token Exchange {@code act} claim, and this
     * deployment uses neither (no TLS_CLIENT_AUTH/SELF_SIGNED_TLS_CLIENT_AUTH client, no Token
     * Exchange grant) -- omitting it is a no-op, not a behavior change, for every grant this
     * service actually issues.
     */
    @Bean
    public OAuth2TokenGenerator<OAuth2Token> tokenGenerator(
            JWKSource<SecurityContext> jwkSource,
            @Nullable OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer) {
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(jwkSource);
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        if (jwtCustomizer != null) {
            jwtGenerator.setJwtCustomizer(jwtCustomizer);
        }
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        return new DelegatingOAuth2TokenGenerator(
                jwtGenerator, accessTokenGenerator, new AlwaysIssueOAuth2RefreshTokenGenerator());
    }

    /** See {@link #tokenGenerator} javadoc -- identical to Spring's own OAuth2RefreshTokenGenerator minus the public-client skip. */
    private static final class AlwaysIssueOAuth2RefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {
        private final StringKeyGenerator refreshTokenGenerator =
                new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96);

        @Override
        public OAuth2RefreshToken generate(OAuth2TokenContext context) {
            if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
                return null;
            }
            Instant issuedAt = Instant.now();
            Instant expiresAt = issuedAt.plus(context.getRegisteredClient().getTokenSettings().getRefreshTokenTimeToLive());
            return new OAuth2RefreshToken(this.refreshTokenGenerator.generateKey(), issuedAt, expiresAt);
        }
    }

    /**
     * The single OAuth2AuthorizationService bean Spring AS wires into its
     * filter chain -- wraps the standard JDBC-backed service (built inline
     * here, not itself a bean) with ReuseDetectingAuthorizationService so
     * refresh-token reuse detection applies to every save()/findByToken()
     * call Spring AS makes, with no other config changes needed.
     */
    @Bean
    public OAuth2AuthorizationService oAuth2AuthorizationService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository,
            RefreshTokenLedgerRepository refreshTokenLedgerRepository) {
        JdbcOAuth2AuthorizationService jdbcService =
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
        JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper rowMapper =
                new JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(registeredClientRepository);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModules(SecurityJackson2Modules.getModules(this.getClass().getClassLoader()));
        objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        rowMapper.setObjectMapper(objectMapper);
        jdbcService.setAuthorizationRowMapper(rowMapper);
        return new ReuseDetectingAuthorizationService(jdbcService, refreshTokenLedgerRepository);
    }
}
