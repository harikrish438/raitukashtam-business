package com.raitukashtam.mycommunity.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Optional;

/**
 * firebase.credentials-json comes from Vault (secret/mycommunity-service),
 * same pattern as aws.s3.access-key -- base64-encoded (the raw service-
 * account JSON contains quotes/colons/braces that are awkward to carry
 * through a single-line env var otherwise). Deliberately optional: when
 * unset (no Firebase project provisioned yet), this bean is empty and
 * NotificationService logs what it would have sent instead of failing --
 * the trigger-point wiring works and is testable today, and real sending
 * starts the moment a real credential is dropped into Vault.
 */
@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.credentials-json:}")
    private String credentialsJsonBase64;

    @Bean
    public Optional<FirebaseMessaging> firebaseMessaging() {
        if (credentialsJsonBase64 == null || credentialsJsonBase64.isBlank()) {
            log.warn("firebase.credentials-json is not configured -- push notifications will be logged only, not actually sent");
            return Optional.empty();
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(credentialsJsonBase64);
            GoogleCredentials credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
            FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options)
                    : FirebaseApp.getInstance();
            return Optional.of(FirebaseMessaging.getInstance(app));
        } catch (Exception e) {
            log.error("Failed to initialize Firebase from firebase.credentials-json -- push notifications will be logged only, not actually sent", e);
            return Optional.empty();
        }
    }
}
