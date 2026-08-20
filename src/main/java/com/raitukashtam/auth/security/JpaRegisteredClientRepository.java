package com.raitukashtam.auth.security;

import com.raitukashtam.auth.entity.Client;
import com.raitukashtam.auth.entity.ClientRedirectUri;
import com.raitukashtam.auth.entity.ClientType;
import com.raitukashtam.auth.repository.ClientRedirectUriRepository;
import com.raitukashtam.auth.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Backs Spring Authorization Server's client lookups with the Client/
 * ClientRedirectUri tables directly -- no separate Spring-owned client
 * table. save() is unsupported: clients are managed via ClientRepository/
 * ClientDataSeeder, not through this abstraction.
 */
@Component
public class JpaRegisteredClientRepository implements RegisteredClientRepository {

    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private ClientRedirectUriRepository clientRedirectUriRepository;

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException(
                "Clients are managed via ClientRepository/ClientDataSeeder, not RegisteredClientRepository.save()");
    }

    @Override
    @Transactional(readOnly = true)
    public RegisteredClient findById(String id) {
        return clientRepository.findById(Long.valueOf(id)).map(this::toRegisteredClient).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public RegisteredClient findByClientId(String clientId) {
        return clientRepository.findByClientId(clientId).map(this::toRegisteredClient).orElse(null);
    }

    private RegisteredClient toRegisteredClient(Client client) {
        RegisteredClient.Builder builder = RegisteredClient.withId(client.getId().toString())
                .clientId(client.getClientId())
                .clientIdIssuedAt(client.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())
                .scope("api")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofSeconds(client.getAccessTokenTtlSeconds()))
                        .refreshTokenTimeToLive(Duration.ofSeconds(client.getRefreshTokenTtlSeconds()))
                        .reuseRefreshTokens(false)
                        .build())
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(client.getClientType() != ClientType.BACKEND_SERVICE)
                        .requireAuthorizationConsent(false)
                        .build());

        if (client.getClientSecretHash() != null) {
            builder.clientSecret(client.getClientSecretHash());
        }

        if (client.getClientType() == ClientType.BACKEND_SERVICE) {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS);
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
            for (ClientRedirectUri redirectUri : clientRedirectUriRepository.findByClient_Id(client.getId())) {
                builder.redirectUri(redirectUri.getUri());
            }
        }

        return builder.build();
    }
}
