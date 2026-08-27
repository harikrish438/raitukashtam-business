package com.raitukashtam.auth.controller;

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
import com.raitukashtam.auth.config.OpenApiConfig;
import com.raitukashtam.auth.security.GooglePayload;
import com.raitukashtam.auth.security.GoogleTokenVerifierService;
import com.raitukashtam.auth.service.RateLimiterService;
import com.raitukashtam.auth.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/google")
@RequiredArgsConstructor
public class GoogleController {

    @Value("${raitukashtam.default-product-code}")
    private String defaultProductCode;

    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final IdentityRepository identityRepository;
    private final IdentityCredentialRepository identityCredentialRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductMembershipRepository productMembershipRepository;
    private final RoleService roleService;
    private final RateLimiterService rateLimiterService;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/verify-token")
    @Transactional
    @Tag(name = OpenApiConfig.TAG_SELF_SERVICE)
    @Operation(summary = "Verify a Google ID token and authenticate the browser session",
            description = "Public, unauthenticated -- called by the frontend with a real Google ID "
                    + "token obtained via Google Identity Services. On success, creates/links an "
                    + "Identity and establishes an authenticated session; the frontend then continues "
                    + "with the same /oauth2/authorize request it already uses for password login.")
    public ResponseEntity<?> verifyGoogleToken(@RequestParam String idToken,
                                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        rateLimiterService.checkLimit("google-verify", httpRequest, 20, java.time.Duration.ofHours(1));

        GooglePayload payload;
        try {
            Optional<GooglePayload> verified = googleTokenVerifierService.verify(idToken);
            if (verified.isEmpty()) {
                return ResponseEntity.badRequest().body("Invalid ID token");
            }
            payload = verified.get();
        } catch (GoogleTokenVerifierService.GoogleVerificationException e) {
            return ResponseEntity.internalServerError().body("Error verifying token: " + e.getMessage());
        }

        String googleSubject = payload.subject();
        String email = payload.email();
        String name = payload.name();
        boolean emailVerified = payload.emailVerified();

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

        // Phase 4b retired direct token issuance in favor of Spring
        // Authorization Server's Authorization Code + PKCE flow, which has
        // no API for "mint a token for this already-authenticated
        // principal" outside that flow. Instead: authenticate the current
        // browser session exactly the way a successful /login does (same
        // principal shape as IdentityAuthenticationProvider -- the identity
        // UUID via a plain UsernamePasswordAuthenticationToken), and let the
        // frontend continue with the SAME /oauth2/authorize request it
        // already uses for password login. With the session now
        // authenticated, /oauth2/authorize proceeds straight to issuing a
        // code instead of redirecting to /login.
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                identity.getId().toString(), null, List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_USER")));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return ResponseEntity.ok().build();
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
