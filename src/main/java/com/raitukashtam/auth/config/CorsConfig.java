package com.raitukashtam.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Registered on /** for both Spring Security filter chains
 * (AuthorizationServerConfig's @Order(1) for /oauth2/**, SecurityConfig's
 * @Order(2) for everything else) -- a real SPA on its own origin needs CORS
 * for the client-side PKCE code exchange (POST /oauth2/token) and plausibly
 * other permitAll() endpoints it calls directly. allowCredentials(false):
 * the PKCE exchange doesn't need cookies cross-origin (the session cookie
 * only matters during the same-origin /login redirect dance), so this
 * avoids the credentials+wildcard footgun entirely. Empty/unset origins
 * list denies all cross-origin requests (fail-closed default).
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${raitukashtam.cors.allowed-origins:}") String allowedOriginsCsv) {
        List<String> origins = Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
