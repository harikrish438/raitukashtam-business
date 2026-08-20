package com.raitukashtam.auth.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.raitukashtam.auth.entity.CredentialType;
import com.raitukashtam.auth.entity.Identity;
import com.raitukashtam.auth.entity.IdentityCredential;
import com.raitukashtam.auth.entity.IdentityStatus;
import com.raitukashtam.auth.entity.MembershipStatus;
import com.raitukashtam.auth.entity.Product;
import com.raitukashtam.auth.entity.ProductMembership;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.exception.AccountLockedException;
import com.raitukashtam.auth.exception.ResourceNotFoundException;
import com.raitukashtam.auth.repository.IdentityCredentialRepository;
import com.raitukashtam.auth.repository.IdentityRepository;
import com.raitukashtam.auth.repository.ProductMembershipRepository;
import com.raitukashtam.auth.repository.ProductRepository;
import com.raitukashtam.auth.repository.UserRepository;
import com.raitukashtam.auth.service.RoleService;
import com.raitukashtam.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.Collections;

@RestController
@RequestMapping("/google")
@RequiredArgsConstructor
public class GoogleController {

    @Value("${google.client-id}")
    private String googleClientId;

    @Value("${raitukashtam.default-product-code}")
    private String defaultProductCode;

    private final IdentityRepository identityRepository;
    private final IdentityCredentialRepository identityCredentialRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductMembershipRepository productMembershipRepository;
    private final RoleService roleService;
    private final TokenService tokenService;

    @PostMapping("/verify-token")
    @Transactional
    public ResponseEntity<?> verifyGoogleToken(@RequestParam String idToken) {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken googleIdToken;
        try {
            googleIdToken = verifier.verify(idToken);
        } catch (GeneralSecurityException | IOException e) {
            return ResponseEntity.internalServerError().body("Error verifying token: " + e.getMessage());
        }

        if (googleIdToken == null) {
            return ResponseEntity.badRequest().body("Invalid ID token");
        }

        GoogleIdToken.Payload payload = googleIdToken.getPayload();
        String googleSubject = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());

        IdentityCredential existingCredential = identityCredentialRepository
                .findByCredentialTypeAndExternalSubject(CredentialType.GOOGLE, googleSubject)
                .orElse(null);

        Identity identity;
        if (existingCredential != null) {
            identity = existingCredential.getIdentity();
        } else {
            Identity existingIdentity = identityRepository.findByPrimaryEmail(email).orElse(null);
            if (existingIdentity != null && !emailVerified) {
                return ResponseEntity.status(403)
                        .body("Cannot link Google account: email not verified by Google");
            }

            identity = existingIdentity != null ? existingIdentity : createIdentity(email);

            IdentityCredential googleCredential = new IdentityCredential();
            googleCredential.setIdentity(identity);
            googleCredential.setCredentialType(CredentialType.GOOGLE);
            googleCredential.setExternalSubject(googleSubject);
            googleCredential.setVerified(emailVerified);
            identityCredentialRepository.save(googleCredential);
        }

        User user = userRepository.findByIdentity_Id(identity.getId())
                .orElseGet(() -> provisionUser(identity, email, name, emailVerified));

        if (user.isLocked()) {
            throw new AccountLockedException("Account is locked. Please contact support.");
        }

        return ResponseEntity.ok(tokenService.issueTokenPair(user));
    }

    private Identity createIdentity(String email) {
        Identity identity = new Identity();
        identity.setPrimaryEmail(email);
        identity.setStatus(IdentityStatus.ACTIVE);
        return identityRepository.save(identity);
    }

    private User provisionUser(Identity identity, String email, String name, boolean emailVerified) {
        String displayName = (name != null && !name.isBlank()) ? name.trim() : email;
        String[] parts = displayName.split(" ", 2);

        User user = new User();
        user.setEmail(email);
        user.setFirstName(parts[0]);
        user.setLastName(parts.length > 1 ? parts[1] : "");
        user.setVerified(emailVerified);
        user.setIdentity(identity);
        user.setCreatedBy(email);
        User savedUser = userRepository.save(user);

        Product defaultProduct = productRepository.findByCode(defaultProductCode)
                .orElseThrow(() -> new ResourceNotFoundException("Default product not found with code: " + defaultProductCode));

        ProductMembership membership = new ProductMembership();
        membership.setIdentity(identity);
        membership.setProduct(defaultProduct);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setJoinedAt(LocalDateTime.now());
        productMembershipRepository.save(membership);
        roleService.assignDefaultRole(membership);

        return savedUser;
    }
}
