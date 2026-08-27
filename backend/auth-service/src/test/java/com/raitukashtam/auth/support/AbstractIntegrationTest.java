package com.raitukashtam.auth.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.raitukashtam.auth.security.GoogleTokenVerifierService;
import com.raitukashtam.auth.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for every real-HTTP integration test. Real Postgres + Redis
 * via Testcontainers (this codebase has hit multiple bugs -- ddl-auto,
 * V10's constraint name -- that only showed up against a genuinely real
 * database, see project memory; mocking the persistence layer would have
 * missed all of them), 2Factor/reCAPTCHA intercepted at the HTTP level via
 * WireMock (real external services, can't hit them from a test run).
 *
 * The Google ID token verifier and email sender are Mockito mocks too, but
 * registered as permanent @Primary beans here (TestDoubles below) rather
 * than via @MockBean on the two test classes that need them -- @MockBean
 * forks a distinct Spring context per class, and RANDOM_PORT was tried
 * first to let multiple forked contexts coexist without a fixed-port
 * collision; that hit a harder wall (app.base-url's ${local.server.port}
 * is consumed by AuthorizationServerConfig's eager @Value field injection,
 * which resolves before the web server has actually started and published
 * that property -- confirmed live, "Could not resolve placeholder
 * 'local.server.port'"). Every test class sharing one identical context
 * (this class's own fixed port, see application-apitest.yml) sidesteps
 * the whole problem instead of solving the timing issue.
 *
 * Containers and WireMock are singletons, started once for the whole test
 * JVM (not per test class) and never explicitly stopped -- Testcontainers'
 * own Ryuk reaper cleans them up on JVM exit. This is deliberate: starting
 * fresh containers per class would also defeat Spring's context caching
 * (a differently-configured @DynamicPropertySource per class looks like a
 * different context to Spring, forcing a full reseed/Flyway-migrate every
 * class instead of once for the whole suite).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("apitest")
// Explicit @Import rather than relying on Spring Boot's nested-static-
// @TestConfiguration auto-detection -- that mechanism scans the
// DECLARING test class, not necessarily superclasses, so leaving this
// implicit risked TestDoubles silently not being picked up by subclasses.
@org.springframework.context.annotation.Import(AbstractIntegrationTest.TestDoubles.class)
public abstract class AbstractIntegrationTest {

    @TestConfiguration
    static class TestDoubles {
        @Bean
        @Primary
        GoogleTokenVerifierService googleTokenVerifierService() {
            return Mockito.mock(GoogleTokenVerifierService.class);
        }

        @Bean
        @Primary
        EmailService emailService() {
            return Mockito.mock(EmailService.class);
        }
    }

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    protected static final WireMockServer WIRE_MOCK =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        // pgjdbc sends "SET TIME ZONE '<jvm default tz>'" during connection
        // setup. On this host the JVM's default zone resolves to the
        // pre-2006 alias "Asia/Calcutta" (not "Asia/Kolkata"), which this
        // postgres:15 image's bundled tzdata doesn't recognize --
        // "FATAL: invalid value for parameter \"TimeZone\": \"Asia/Calcutta\"",
        // confirmed live. Forcing UTC here sidesteps the alias entirely;
        // these tests don't assert on timezone-sensitive behavior, so UTC
        // vs. Asia/Kolkata makes no difference to what's being verified.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        POSTGRES.start();
        REDIS.start();
        WIRE_MOCK.start();
        WireMock.configureFor("localhost", WIRE_MOCK.port());
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");

        registry.add("twofactor.api.url",
                () -> WIRE_MOCK.baseUrl() + "/2factor/%s/SMS/%s/AUTOGEN");
        registry.add("twofactor.api.verify-url",
                () -> WIRE_MOCK.baseUrl() + "/2factor/%s/SMS/VERIFY/%s/%s");
        registry.add("google.recaptcha.verify-url",
                () -> WIRE_MOCK.baseUrl() + "/recaptcha/siteverify");
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    protected GoogleTokenVerifierService googleTokenVerifierService;

    @Autowired
    protected EmailService emailService;

    /**
     * Redis is a singleton shared across the whole suite (see class javadoc
     * for why), so anything it holds -- rate-limit counters, OTP sessions,
     * HTTP sessions -- accumulates across every test method unless reset.
     * Rate limiters are IP-keyed and every TestRestTemplate call comes from
     * the same loopback address, so without this a rate-limit test's result
     * depends on how many other tests already hit that endpoint first --
     * confirmed flaky in practice before adding this. Flushing before
     * (not after) each test means a failed test's Redis state is still
     * there afterward to inspect if needed. The two shared Mockito mocks
     * are singleton beans too (one instance for the whole suite), so reset
     * their stubbings/invocations for the same reason.
     */
    @BeforeEach
    void flushRedisBeforeEachTest() {
        redisConnectionFactory.getConnection().serverCommands().flushAll();
        Mockito.reset(googleTokenVerifierService, emailService);
    }

    /**
     * WireMock stubs accumulate across tests sharing the singleton server --
     * reset before each test class's stubbing so one class's stubs can't
     * leak into another's assertions (e.g. a stale "OTP always succeeds"
     * stub silently masking a test that expects failure).
     */
    protected static void resetWireMock() {
        WIRE_MOCK.resetAll();
    }

    protected String baseUrl(String path) {
        return "http://localhost:18080" + path;
    }
}
