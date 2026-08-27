package com.raitukashtam.auth.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;

@Service
public class GoogleTokenVerifierServiceImpl implements GoogleTokenVerifierService {

    @Value("${google.client-id}")
    private String googleClientId;

    @Override
    public Optional<GooglePayload> verify(String idToken) {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken googleIdToken;
        try {
            googleIdToken = verifier.verify(idToken);
        } catch (GeneralSecurityException | IOException e) {
            throw new GoogleVerificationException("Error verifying token: " + e.getMessage(), e);
        }
        if (googleIdToken == null) {
            return Optional.empty();
        }

        GoogleIdToken.Payload payload = googleIdToken.getPayload();
        return Optional.of(new GooglePayload(
                payload.getSubject(),
                payload.getEmail(),
                (String) payload.get("name"),
                Boolean.TRUE.equals(payload.getEmailVerified())));
    }
}
