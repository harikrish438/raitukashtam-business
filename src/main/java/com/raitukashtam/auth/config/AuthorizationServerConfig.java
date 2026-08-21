package com.raitukashtam.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.raitukashtam.auth.repository.RefreshTokenLedgerRepository;
import com.raitukashtam.auth.security.ReuseDetectingAuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
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
            HttpSecurity http, CorsConfigurationSource corsConfigurationSource) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
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
