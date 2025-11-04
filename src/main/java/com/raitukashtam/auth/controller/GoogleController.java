package com.raitukashtam.auth.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@RestController
@RequestMapping("/google")
@RequiredArgsConstructor
public class GoogleController {

    @Value("${google.client-id}")
    private String googleClientId;

    @PostMapping("/verify-token")
    public ResponseEntity<?> verifyGoogleToken(@RequestParam String idToken) {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        try {
            GoogleIdToken googleIdToken = verifier.verify(idToken);
            if (googleIdToken != null) {
                GoogleIdToken.Payload payload = googleIdToken.getPayload();
                
                // Get profile information from payload
                String userId = payload.getSubject();
                String email = payload.getEmail();
                String name = (String) payload.get("name");
                boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
                
                // Here you can implement your own logic, e.g., create/update user in your database
                // and generate your own JWT token
                
                return ResponseEntity.ok("Token verified successfully. User: " + name + ", Email: " + email);
            } else {
                return ResponseEntity.badRequest().body("Invalid ID token");
            }
        } catch (GeneralSecurityException | IOException e) {
            return ResponseEntity.internalServerError().body("Error verifying token: " + e.getMessage());
        }
    }
}
