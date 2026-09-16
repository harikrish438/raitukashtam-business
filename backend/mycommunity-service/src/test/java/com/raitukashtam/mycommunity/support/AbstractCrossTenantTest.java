package com.raitukashtam.mycommunity.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Shared base for the cross-tenant isolation suite: real Postgres via
 * Testcontainers (same rationale as auth-service's AbstractIntegrationTest
 * -- this is exactly the kind of guard behavior that needs a real DB, not
 * a mocked repository, to mean anything), plus a locally-generated RSA
 * keypair whose public half is served as a JWKS via WireMock (mimicking
 * auth-service's real /oauth2/jwks endpoint, which mycommunity-service's
 * resource-server config points at by default). Minting a token here never
 * touches a real auth-service -- this suite only exercises endpoints that
 * take an identityId string and never call back into AuthServiceClient
 * (community/member/resource setup is seeded directly via repositories,
 * not through createCommunity/activateInvitations/createJoinRequest).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("apitest")
public abstract class AbstractCrossTenantTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    protected static final WireMockServer WIRE_MOCK =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    private static RSAKey RSA_JWK;

    static {
        // Same pre-2006 tz-alias issue AbstractIntegrationTest documents in
        // auth-service -- forcing UTC sidesteps it; nothing here asserts on
        // timezone-sensitive behavior.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        POSTGRES.start();
        WIRE_MOCK.start();
        WireMock.configureFor("localhost", WIRE_MOCK.port());

        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSA_JWK = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(UUID.randomUUID().toString())
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate test RSA keypair", e);
        }

        WIRE_MOCK.stubFor(WireMock.get(WireMock.urlEqualTo("/oauth2/jwks"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[" + RSA_JWK.toPublicJWK().toJSONString() + "]}")));
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> WIRE_MOCK.baseUrl() + "/oauth2/jwks");
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    /**
     * A real RS256 JWT, signed with this suite's own test keypair and
     * verifiable against the WireMock-served JWKS above -- the "sub" claim
     * is the only thing any guard code in this service actually reads
     * (see CommunityController's jwt.getSubject() calls), so that's the
     * only claim that matters for these tests.
     */
    protected String mintToken(String identityId) {
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(JOSEObjectType.JWT)
                    .keyID(RSA_JWK.getKeyID())
                    .build();
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(identityId)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(3600)))
                    .build();
            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new RSASSASigner(RSA_JWK.toRSAPrivateKey()));
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to mint test JWT", e);
        }
    }

    protected String baseUrl(String path) {
        return "http://localhost:18081" + path;
    }
}
