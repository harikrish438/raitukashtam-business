package com.raitukashtam.auth.response;

import com.raitukashtam.auth.entity.ClientType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientResponse {
    private Long id;
    private String productCode;
    private String clientId;
    private ClientType clientType;
    private List<String> redirectUris;
    private int accessTokenTtlSeconds;
    private int refreshTokenTtlSeconds;

    /**
     * Only ever populated on the POST response for a freshly-created
     * BACKEND_SERVICE client -- always null elsewhere. Never derived from
     * Client.clientSecretHash; callers must build this field-by-field, not
     * via ModelMapper, so the hash can never be wired through by accident.
     */
    private String clientSecret;

    private LocalDateTime createdAt;
}
