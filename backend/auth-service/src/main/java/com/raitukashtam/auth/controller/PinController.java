package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.config.OpenApiConfig;
import com.raitukashtam.auth.entity.CredentialType;
import com.raitukashtam.auth.entity.Identity;
import com.raitukashtam.auth.entity.IdentityCredential;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.exception.AccountLockedException;
import com.raitukashtam.auth.exception.ResourceAlreadyExistsException;
import com.raitukashtam.auth.exception.ResourceNotFoundException;
import com.raitukashtam.auth.repository.IdentityCredentialRepository;
import com.raitukashtam.auth.repository.IdentityRepository;
import com.raitukashtam.auth.repository.UserRepository;
import com.raitukashtam.auth.response.PinDeviceResponse;
import com.raitukashtam.auth.service.PinAttemptService;
import com.raitukashtam.auth.service.RateLimiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Device-bound app PIN: register a PIN for one specific device (after a
 * real login), then re-authenticate on that device with just the PIN
 * instead of repeating OTP verification. Server-verified on every use
 * (not a local/client-side gate over a stored token) so a lost device's
 * PIN can be revoked centrally -- see UserController's admin-revoke
 * endpoint. Mirrors OtpController's "verify a credential, then continue
 * through the normal /oauth2/authorize PKCE flow" shape.
 */
@RestController
@RequestMapping("/pin")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_SELF_SERVICE)
public class PinController {

    private final IdentityRepository identityRepository;
    private final IdentityCredentialRepository identityCredentialRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PinAttemptService pinAttemptService;
    private final RateLimiterService rateLimiterService;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/register")
    @Transactional
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Register (or replace) this device's PIN", description = "Requires a Bearer "
            + "token from a completed login. Re-registering the same deviceId for the same identity "
            + "updates the PIN (e.g. the user changing it); a deviceId already registered to a "
            + "different identity is a 409.")
    public ResponseEntity<Void> registerDevice(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam String deviceId,
                                                @RequestParam String pin) {
        UUID identityId = resolveIdentityId(jwt);
        Identity identity = identityRepository.findById(identityId)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid or unknown identity"));

        IdentityCredential credential = identityCredentialRepository
                .findByCredentialTypeAndExternalSubject(CredentialType.DEVICE_PIN, deviceId)
                .orElseGet(IdentityCredential::new);

        if (credential.getId() != null && !credential.getIdentity().getId().equals(identityId)) {
            throw new ResourceAlreadyExistsException("This device is already registered to a different account");
        }

        credential.setIdentity(identity);
        credential.setCredentialType(CredentialType.DEVICE_PIN);
        credential.setExternalSubject(deviceId);
        credential.setPasswordHash(passwordEncoder.encode(pin));
        credential.setVerified(true);
        identityCredentialRepository.save(credential);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    @Transactional
    @Operation(summary = "Authenticate the browser/app session with a device PIN", description =
            "Public, unauthenticated, rate-limited (10/hour/IP) plus a per-device lockout after "
            + "repeated wrong PINs. Same session-authentication mechanism as /otp/login -- the "
            + "client then continues with the normal /oauth2/authorize (Authorization Code + PKCE) "
            + "request.")
    public ResponseEntity<?> loginWithPin(@RequestParam String deviceId,
                                           @RequestParam String pin,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse) {
        rateLimiterService.checkLimit("pin-login", httpRequest, 10, Duration.ofHours(1));
        pinAttemptService.checkNotLocked(deviceId);

        IdentityCredential credential = identityCredentialRepository
                .findByCredentialTypeAndExternalSubject(CredentialType.DEVICE_PIN, deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("No PIN registered for this device"));

        if (!passwordEncoder.matches(pin, credential.getPasswordHash())) {
            pinAttemptService.recordFailure(deviceId);
            return ResponseEntity.badRequest().body("Invalid PIN");
        }

        Identity identity = credential.getIdentity();
        User user = userRepository.findByIdentity_Id(identity.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found for this identity"));

        if (user.isLocked()) {
            throw new AccountLockedException("Account is locked. Please contact support.");
        }

        pinAttemptService.recordSuccess(deviceId);

        // Same pattern as OtpController.loginWithOtp/GoogleController.verifyGoogleToken:
        // authenticate the current session, then let the client continue
        // with the standard /oauth2/authorize PKCE request.
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                identity.getId().toString(), null, List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/devices")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List the caller's own registered PIN devices", description =
            "Requires a Bearer token. Never returns the PIN hash.")
    public List<PinDeviceResponse> listDevices(@AuthenticationPrincipal Jwt jwt) {
        UUID identityId = resolveIdentityId(jwt);
        return identityCredentialRepository
                .findAllByIdentity_IdAndCredentialType(identityId, CredentialType.DEVICE_PIN)
                .stream()
                .map(c -> new PinDeviceResponse(c.getExternalSubject(), c.isVerified(), c.getCreatedAt()))
                .toList();
    }

    @DeleteMapping("/devices/{deviceId}")
    @Transactional
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Revoke one of the caller's own PIN devices", description =
            "Requires a Bearer token. 404 if the device doesn't exist or isn't the caller's own -- "
            + "same reasoning as /users/me's 404 choice, to avoid confirming another identity's "
            + "device exists.")
    public ResponseEntity<Void> revokeOwnDevice(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable String deviceId) {
        UUID identityId = resolveIdentityId(jwt);
        IdentityCredential credential = identityCredentialRepository
                .findByCredentialTypeAndExternalSubject(CredentialType.DEVICE_PIN, deviceId)
                .filter(c -> c.getIdentity().getId().equals(identityId))
                .orElseThrow(() -> new ResourceNotFoundException("No such device"));
        identityCredentialRepository.delete(credential);
        return ResponseEntity.noContent().build();
    }

    private UUID resolveIdentityId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new UsernameNotFoundException("Invalid or unknown identity");
        }
    }
}
