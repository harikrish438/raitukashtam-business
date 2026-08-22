package com.raitukashtam.auth.support;

import com.raitukashtam.auth.entity.Client;
import com.raitukashtam.auth.entity.ClientType;
import com.raitukashtam.auth.entity.Identity;
import com.raitukashtam.auth.entity.Product;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.repository.ClientRepository;
import com.raitukashtam.auth.repository.IdentityRepository;
import com.raitukashtam.auth.repository.ProductRepository;
import com.raitukashtam.auth.repository.UserRepository;
import com.raitukashtam.auth.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-only support bean (lives under src/test but in the same base
 * package as the application, so component-scan picks it up in
 * @SpringBootTest contexts). Registration goes through the real
 * UserService.registerUser -- same code path /users/register uses --
 * rather than hand-building entities, so test setup can't silently drift
 * from what registration actually does. Direct repository access is only
 * for states unreachable via any API (promoting to PLATFORM_ADMIN with no
 * existing admin, locking an account, a client with a known plaintext
 * secret since ClientDataSeeder's is randomly generated and unrecoverable).
 */
@Component
public class TestDataFactory {

    private static final AtomicLong MOBILE_SEQ = new AtomicLong(9_000_000_000L);

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private IdentityRepository identityRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${raitukashtam.default-product-code}")
    private String defaultProductCode;

    /** A 10-digit mobile number guaranteed unique within this test JVM run. */
    public String uniqueMobile() {
        return String.valueOf(MOBILE_SEQ.incrementAndGet());
    }

    public String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }

    public static final String VALID_PASSWORD = "Password1@";

    @Transactional
    public User registerUser(String email) {
        return userService.registerUser(email, VALID_PASSWORD, "Test", "User", uniqueMobile());
    }

    @Transactional
    public User registerUser(String email, String password) {
        return userService.registerUser(email, password, "Test", "User", uniqueMobile());
    }

    @Transactional
    public User registerAndPromoteToPlatformAdmin(String email) {
        User user = registerUser(email);
        Identity identity = user.getIdentity();
        identity.setPlatformAdmin(true);
        identityRepository.save(identity);
        return user;
    }

    @Transactional
    public User lockAccount(User user) {
        user.setLocked(true);
        return userRepository.save(user);
    }

    /** A BACKEND_SERVICE client with a caller-known plaintext secret, in the default product. */
    @Transactional
    public String createBackendServiceClient(String clientId) {
        Product product = productRepository.findByCode(defaultProductCode)
                .orElseThrow(() -> new IllegalStateException("Default product not seeded: " + defaultProductCode));

        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        String plaintextSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Client client = new Client();
        client.setProduct(product);
        client.setClientId(clientId);
        client.setClientType(ClientType.BACKEND_SERVICE);
        client.setClientSecretHash(passwordEncoder.encode(plaintextSecret));
        client.setAccessTokenTtlSeconds(3600);
        client.setRefreshTokenTtlSeconds(604800);
        clientRepository.save(client);

        return plaintextSecret;
    }
}
