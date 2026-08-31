package com.raitukashtam.mycommunity.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Calls auth-service's GET /users/me to resolve the caller's real profile
 * (mobile number, name) from their own Bearer token -- reintroduces the
 * cross-service call this service's own CLAUDE.md flagged as removed
 * (Phase 1 never needed it; community registration trusting a
 * client-supplied admin mobile number instead of this is the trust gap
 * that motivated adding it back).
 */
@Slf4j
@Component
public class AuthServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String authServiceUrl;

    public AuthServiceClient(@Value("${auth.service.url}") String authServiceUrl) {
        this.authServiceUrl = authServiceUrl;
    }

    /** callerToken is the raw JWT value (Jwt.getTokenValue()), no "Bearer " prefix. */
    public AuthUserProfile getCurrentUserProfile(String callerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + callerToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            return restTemplate.exchange(
                    authServiceUrl + "/users/me", HttpMethod.GET, entity, AuthUserProfile.class).getBody();
        } catch (RestClientException e) {
            log.error("Failed to resolve caller profile from auth-service at {}/users/me", authServiceUrl, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not resolve caller's profile from auth-service", e);
        }
    }
}
