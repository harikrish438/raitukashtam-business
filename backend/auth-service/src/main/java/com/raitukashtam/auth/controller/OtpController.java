package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.config.OpenApiConfig;
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
import com.raitukashtam.auth.service.OTPService;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/otp")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_SELF_SERVICE)
public class OtpController {

    @Value("${raitukashtam.mycommunity-product-code}")
    private String myCommunityProductCode;

    private final OTPService otpService;
    private final RateLimiterService rateLimiterService;
    private final IdentityRepository identityRepository;
    private final IdentityCredentialRepository identityCredentialRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductMembershipRepository productMembershipRepository;
    private final RoleService roleService;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/generate")
    @Operation(summary = "Send an OTP by SMS/voice call", description = "Public, unauthenticated, "
            + "rate-limited (5/hour/IP). Delegates to 2Factor.in's own AUTOGEN flow -- this service "
            + "never generates or sees the code.")
    public ResponseEntity<Void> generateOtp(@RequestParam String mobileNumber, HttpServletRequest request) {
        rateLimiterService.checkLimit("otp-generate", request, 5, Duration.ofHours(1));
        otpService.generateAndSendOtp(mobileNumber);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify an OTP code", description = "Public, unauthenticated, rate-limited "
            + "(10/hour/IP). Single-use -- the outstanding session is consumed on a successful match. "
            + "This only confirms the code was correct -- it does not authenticate anything. Use "
            + "/otp/login instead if the goal is to sign the caller in.")
    public ResponseEntity<?> verifyOtp(@RequestParam String mobileNumber,
                                        @RequestParam String otp,
                                        HttpServletRequest request) {
        rateLimiterService.checkLimit("otp-verify", request, 10, Duration.ofHours(1));
        if (otpService.validateOtp(mobileNumber, otp)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.badRequest().body("Invalid or expired OTP");
    }

    @PostMapping("/login")
    @Transactional
    @Operation(summary = "Verify an OTP and authenticate the browser/app session", description =
            "Public, unauthenticated, rate-limited (10/hour/IP). Finds or creates the Identity/User "
            + "behind this mobile number (linking to an existing account registered under it via another "
            + "method, e.g. password, if one exists -- otherwise provisioning a brand-new phone-only "
            + "account), ensures a MyCommunity product membership (this is mycommunity's own login path, "
            + "kept separate from the generic default RAITUKASHTAM product other self-service flows use), "
            + "and establishes an authenticated session, the same way /google/verify-token does for "
            + "Google sign-in. The client then continues with the same /oauth2/authorize (Authorization "
            + "Code + PKCE) request against the mycommunity-android client -- with the session now "
            + "authenticated, that proceeds straight to issuing a code instead of redirecting to /login.")
    public ResponseEntity<?> loginWithOtp(@RequestParam String mobileNumber,
                                           @RequestParam String otp,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse) {
        rateLimiterService.checkLimit("otp-login", httpRequest, 10, Duration.ofHours(1));
        if (!otpService.validateOtp(mobileNumber, otp)) {
            return ResponseEntity.badRequest().body("Invalid or expired OTP");
        }

        IdentityCredential existingCredential = identityCredentialRepository
                .findByCredentialTypeAndExternalSubject(CredentialType.OTP_PHONE, mobileNumber)
                .orElse(null);

        Identity identity;
        if (existingCredential != null) {
            identity = existingCredential.getIdentity();
        } else {
            // This phone may already have an account from another login method
            // (e.g. password registration with this mobile number) -- link to
            // that Identity rather than creating a duplicate one.
            Identity linkedIdentity = userRepository.findByMobileNumber(mobileNumber)
                    .map(User::getIdentity)
                    .orElse(null);
            identity = linkedIdentity != null ? linkedIdentity : createIdentityForPhone(mobileNumber);

            IdentityCredential otpCredential = new IdentityCredential();
            otpCredential.setIdentity(identity);
            otpCredential.setCredentialType(CredentialType.OTP_PHONE);
            otpCredential.setExternalSubject(mobileNumber);
            otpCredential.setVerified(true);
            identityCredentialRepository.save(otpCredential);
        }

        User user = userRepository.findByIdentity_Id(identity.getId())
                .orElseGet(() -> provisionUserForPhone(identity, mobileNumber));

        if (user.isLocked()) {
            throw new AccountLockedException("Account is locked. Please contact support.");
        }

        // Every successful OTP login is a mycommunity login -- ensure MyCommunity
        // membership even when `identity` was linked to a pre-existing account
        // registered under a different product (e.g. password + RAITUKASHTAM),
        // not just for brand-new phone-only signups.
        ensureMyCommunityMembership(identity);

        // Same pattern as GoogleController.verifyGoogleToken(): authenticate the
        // current session exactly like a successful /login does, then let the
        // client continue with the standard /oauth2/authorize PKCE request.
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                identity.getId().toString(), null, List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_USER")));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return ResponseEntity.ok().build();
    }

    private Identity createIdentityForPhone(String mobileNumber) {
        Identity identity = new Identity();
        identity.setPrimaryEmail(placeholderEmail(mobileNumber));
        identity.setPrimaryPhone(mobileNumber);
        identity.setStatus(IdentityStatus.ACTIVE);
        return identityRepository.save(identity);
    }

    private User provisionUserForPhone(Identity identity, String mobileNumber) {
        User user = new User();
        user.setEmail(placeholderEmail(mobileNumber));
        user.setFirstName("User");
        user.setLastName(mobileNumber);
        user.setVerified(true);
        user.setMobileNumber(mobileNumber);
        user.setIdentity(identity);
        user.setCreatedBy(mobileNumber);
        return userRepository.save(user);
    }

    /**
     * Idempotent: a linked pre-existing identity may already have a
     * MyCommunity membership from an earlier OTP login, a brand-new one
     * never does.
     */
    private void ensureMyCommunityMembership(Identity identity) {
        if (productMembershipRepository.findByIdentity_IdAndProduct_Code(identity.getId(), myCommunityProductCode).isPresent()) {
            return;
        }

        Product myCommunityProduct = productRepository.findByCode(myCommunityProductCode)
                .orElseThrow(() -> new ResourceNotFoundException("MyCommunity product not found with code: " + myCommunityProductCode));

        ProductMembership membership = new ProductMembership();
        membership.setIdentity(identity);
        membership.setProduct(myCommunityProduct);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setJoinedAt(LocalDateTime.now());
        productMembershipRepository.save(membership);
        roleService.assignDefaultRole(membership);
    }

    /**
     * Identity.primaryEmail and User.email are both NOT NULL + unique, but a
     * phone-only signup has no real email -- synthesize a stable, deterministic
     * placeholder keyed off the mobile number (same trick GoogleController
     * falls back to when Google gives no name).
     */
    private String placeholderEmail(String mobileNumber) {
        return mobileNumber + "@phone.mysociety.internal";
    }
}
