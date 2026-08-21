package com.raitukashtam.auth.config;

import com.raitukashtam.auth.security.CaptchaAwareAuthenticationFilter;
import com.raitukashtam.auth.security.IdentityAuthenticationProvider;
import com.raitukashtam.auth.security.LoginFailureHandler;
import com.raitukashtam.auth.service.LoginAttemptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * @Order(2) chain -- everything except /oauth2/** + /.well-known/**
 * (AuthorizationServerConfig's @Order(1) chain covers those). Phase 4b:
 * the local JwtAuthenticationFilter is gone, replaced by Spring's own
 * oauth2ResourceServer support validating against the same JwtDecoder
 * (RS256/JWKS) AuthorizationServerConfig defines; login is handled by
 * CaptchaAwareAuthenticationFilter/IdentityAuthenticationProvider instead
 * of Spring's default UsernamePasswordAuthenticationFilter, since the
 * default has no slot for the recaptchaToken field.
 */
@Configuration
public class SecurityConfig {

    @Autowired
    private IdentityAuthenticationProvider identityAuthenticationProvider;
    @Autowired
    private LoginAttemptService loginAttemptService;

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(identityAuthenticationProvider);
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(
            HttpSecurity http, JwtDecoder jwtDecoder, CorsConfigurationSource corsConfigurationSource) throws Exception {
        // Built and added manually (not via .formLogin()), so it isn't
        // auto-wired to a session-based SecurityContextRepository the way
        // the DSL's own filters are -- must be set explicitly, and the same
        // instance shared with .securityContext() below so the read side
        // (SecurityContextHolderFilter) agrees with what this filter saves.
        SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
        CaptchaAwareAuthenticationFilter loginFilter =
                new CaptchaAwareAuthenticationFilter(authenticationManager(), loginAttemptService);
        loginFilter.setSecurityContextRepository(securityContextRepository);
        loginFilter.setAuthenticationSuccessHandler(new SavedRequestAwareAuthenticationSuccessHandler());
        loginFilter.setAuthenticationFailureHandler(new LoginFailureHandler());

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }
            String platformRole = jwt.getClaimAsString("platform_role");
            if (platformRole != null) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + platformRole));
            }
            return authorities;
        });

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/users").hasRole("PLATFORM_ADMIN")
                        .requestMatchers("/products/**").hasRole("PLATFORM_ADMIN")
                        .requestMatchers("/**").permitAll()
                        .anyRequest().authenticated()
                )
                // Bearer-token endpoints stay effectively stateless, but the
                // login page itself needs a session to survive the
                // /oauth2/authorize -> /login -> back redirect round trip
                // (already Redis-backed, see application.yml).
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }
}
