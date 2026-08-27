package com.raitukashtam.auth.config;

import com.raitukashtam.auth.entity.Client;
import com.raitukashtam.auth.entity.ClientRedirectUri;
import com.raitukashtam.auth.entity.ClientType;
import com.raitukashtam.auth.entity.Product;
import com.raitukashtam.auth.repository.ClientRedirectUriRepository;
import com.raitukashtam.auth.repository.ClientRepository;
import com.raitukashtam.auth.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Seeds the default product's OAuth2 clients if they don't already exist.
 * Idempotent, same pattern as ProductDataSeeder. raitukashtam-web/-android/
 * -ios are dormant until Phase 4b (Authorization Code + PKCE); only the
 * BACKEND_SERVICE client is reachable in Phase 4a (client_credentials).
 * Must run after ProductDataSeeder (@Order(1)) -- requires the default
 * product to already exist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(2)
public class ClientDataSeeder implements CommandLineRunner {

    private static final int DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 3600;
    private static final int DEFAULT_REFRESH_TOKEN_TTL_SECONDS = 604800;

    private final ClientRepository clientRepository;
    private final ClientRedirectUriRepository clientRedirectUriRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${raitukashtam.default-product-code}")
    private String defaultProductCode;

    @Value("${raitukashtam.web-client.redirect-uri}")
    private String webClientRedirectUri;

    @Override
    @Transactional
    public void run(String... args) {
        Product product = productRepository.findByCode(defaultProductCode)
                .orElseThrow(() -> new IllegalStateException("Default product not found: " + defaultProductCode));

        seedPublicClient("raitukashtam-web", ClientType.WEB_SPA, product, webClientRedirectUri);
        seedPublicClient("raitukashtam-android", ClientType.ANDROID, product, "raitukashtam://callback");
        seedPublicClient("raitukashtam-ios", ClientType.IOS, product, "raitukashtam://callback");
        seedBackendServiceClient("raitukashtam-backend-test", product);
    }

    private void seedPublicClient(String clientId, ClientType type, Product product, String redirectUri) {
        if (clientRepository.existsByClientId(clientId)) {
            return;
        }
        Client client = new Client();
        client.setProduct(product);
        client.setClientId(clientId);
        client.setClientType(type);
        client.setAccessTokenTtlSeconds(DEFAULT_ACCESS_TOKEN_TTL_SECONDS);
        client.setRefreshTokenTtlSeconds(DEFAULT_REFRESH_TOKEN_TTL_SECONDS);
        Client saved = clientRepository.save(client);

        ClientRedirectUri uri = new ClientRedirectUri();
        uri.setClient(saved);
        uri.setUri(redirectUri);
        clientRedirectUriRepository.save(uri);

        log.info("Seeded public client '{}' with redirect URI '{}'", clientId, redirectUri);
    }

    private void seedBackendServiceClient(String clientId, Product product) {
        if (clientRepository.existsByClientId(clientId)) {
            return;
        }
        String plaintextSecret = generateSecret();

        Client client = new Client();
        client.setProduct(product);
        client.setClientId(clientId);
        client.setClientType(ClientType.BACKEND_SERVICE);
        client.setClientSecretHash(passwordEncoder.encode(plaintextSecret));
        client.setAccessTokenTtlSeconds(DEFAULT_ACCESS_TOKEN_TTL_SECONDS);
        client.setRefreshTokenTtlSeconds(DEFAULT_REFRESH_TOKEN_TTL_SECONDS);
        clientRepository.save(client);

        log.warn("Seeded backend-service client '{}' with a one-time generated secret -- "
                + "save it now, it is not recoverable from the database: {}", clientId, plaintextSecret);
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
