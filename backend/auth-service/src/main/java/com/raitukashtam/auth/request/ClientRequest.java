package com.raitukashtam.auth.request;

import com.raitukashtam.auth.entity.ClientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ClientRequest {
    @NotBlank(message = "Client ID is required")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Client ID can only contain letters, numbers, hyphens, and underscores")
    @Size(min = 3, max = 100, message = "Client ID must be between 3 and 100 characters")
    private String clientId;

    @NotNull(message = "Client type is required")
    private ClientType clientType;

    /**
     * Required (non-empty) for WEB_SPA/ANDROID/IOS clients, must be absent
     * or empty for BACKEND_SERVICE clients -- enforced in ClientService
     * since it's a conditional rule, not a plain bean-validation annotation.
     */
    private List<@NotBlank String> redirectUris;

    @Positive(message = "Access token TTL must be positive")
    private Integer accessTokenTtlSeconds;

    @Positive(message = "Refresh token TTL must be positive")
    private Integer refreshTokenTtlSeconds;
}
