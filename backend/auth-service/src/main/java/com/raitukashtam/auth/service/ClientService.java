package com.raitukashtam.auth.service;

import com.raitukashtam.auth.entity.Client;
import com.raitukashtam.auth.entity.ClientRedirectUri;
import com.raitukashtam.auth.entity.ClientType;
import com.raitukashtam.auth.entity.Product;
import com.raitukashtam.auth.exception.InvalidClientConfigurationException;
import com.raitukashtam.auth.exception.ResourceAlreadyExistsException;
import com.raitukashtam.auth.exception.ResourceNotFoundException;
import com.raitukashtam.auth.repository.ClientRedirectUriRepository;
import com.raitukashtam.auth.repository.ClientRepository;
import com.raitukashtam.auth.repository.ProductRepository;
import com.raitukashtam.auth.request.ClientRequest;
import com.raitukashtam.auth.response.ClientResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Admin API path for registering Clients under a Product (Phase 5).
 * Deliberately not shared with ClientDataSeeder -- that seeder is
 * idempotent/startup-time with hardcoded IDs and no caller-supplied input,
 * while this validates arbitrary admin-supplied requests; a shared helper
 * would abstract over two dissimilar callers for no real benefit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    private static final int DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 3600;
    private static final int DEFAULT_REFRESH_TOKEN_TTL_SECONDS = 604800;

    private final ClientRepository clientRepository;
    private final ClientRedirectUriRepository clientRedirectUriRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ClientResponse createClient(String productCode, ClientRequest request, String createdBy) {
        Product product = productRepository.findByCode(productCode)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with code: " + productCode));

        if (clientRepository.existsByClientId(request.getClientId())) {
            throw new ResourceAlreadyExistsException("Client with id " + request.getClientId() + " already exists");
        }

        boolean isBackendService = request.getClientType() == ClientType.BACKEND_SERVICE;
        List<String> redirectUris = request.getRedirectUris() == null ? Collections.emptyList() : request.getRedirectUris();

        if (isBackendService && !redirectUris.isEmpty()) {
            throw new InvalidClientConfigurationException("BACKEND_SERVICE clients must not have redirect URIs");
        }
        if (!isBackendService && redirectUris.isEmpty()) {
            throw new InvalidClientConfigurationException(
                    request.getClientType() + " clients require at least one redirect URI");
        }

        Client client = new Client();
        client.setProduct(product);
        client.setClientId(request.getClientId());
        client.setClientType(request.getClientType());
        client.setCreatedBy(createdBy);
        client.setAccessTokenTtlSeconds(
                request.getAccessTokenTtlSeconds() != null ? request.getAccessTokenTtlSeconds() : DEFAULT_ACCESS_TOKEN_TTL_SECONDS);
        client.setRefreshTokenTtlSeconds(
                request.getRefreshTokenTtlSeconds() != null ? request.getRefreshTokenTtlSeconds() : DEFAULT_REFRESH_TOKEN_TTL_SECONDS);

        String plaintextSecret = null;
        if (isBackendService) {
            plaintextSecret = generateSecret();
            client.setClientSecretHash(passwordEncoder.encode(plaintextSecret));
        }

        Client saved = clientRepository.save(client);

        for (String uri : redirectUris) {
            ClientRedirectUri redirectUri = new ClientRedirectUri();
            redirectUri.setClient(saved);
            redirectUri.setUri(uri);
            clientRedirectUriRepository.save(redirectUri);
        }

        if (isBackendService) {
            log.warn("Registered backend-service client '{}' with a one-time generated secret -- "
                    + "save it now, it is not recoverable from the database: {}", saved.getClientId(), plaintextSecret);
        }

        return toResponse(saved, redirectUris, plaintextSecret);
    }

    public List<ClientResponse> listClients(String productCode) {
        if (!productRepository.existsByCode(productCode)) {
            throw new ResourceNotFoundException("Product not found with code: " + productCode);
        }
        return clientRepository.findByProduct_Code(productCode).stream()
                .map(client -> toResponse(
                        client,
                        clientRedirectUriRepository.findByClient_Id(client.getId()).stream().map(ClientRedirectUri::getUri).toList(),
                        null))
                .toList();
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ClientResponse toResponse(Client client, List<String> redirectUris, String plaintextSecret) {
        return new ClientResponse(
                client.getId(),
                client.getProduct().getCode(),
                client.getClientId(),
                client.getClientType(),
                redirectUris,
                client.getAccessTokenTtlSeconds(),
                client.getRefreshTokenTtlSeconds(),
                plaintextSecret,
                client.getCreatedAt());
    }
}
