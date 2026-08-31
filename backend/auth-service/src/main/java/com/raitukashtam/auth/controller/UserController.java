package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.config.OpenApiConfig;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.request.PlatformAdminRequest;
import com.raitukashtam.auth.request.RegisterRequest;
import com.raitukashtam.auth.request.UpdateProfileRequest;
import com.raitukashtam.auth.response.UserResponse;
import com.raitukashtam.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    @Tag(name = OpenApiConfig.TAG_SELF_SERVICE)
    @Operation(summary = "Register a new user", description = "Public, unauthenticated. Creates an "
            + "Identity + User in the default product.")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest request) {
        User user = userService.registerUser(
                request.getEmail(),
                request.getPassword(),
                request.getFirstName(),
                request.getLastName(),
                request.getMobileNumber()
        );
        return ResponseEntity.ok("User registered successfully");
    }

    @GetMapping("/me")
    @Tag(name = OpenApiConfig.TAG_SELF_SERVICE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get the caller's own profile", description = "Requires a Bearer token backed "
            + "by a real Identity (a user login, not client_credentials). A client_credentials token's "
            + "subject is the OAuth2 client id, not an Identity UUID, so it 404s the same as an unknown "
            + "identity -- this endpoint doesn't distinguish the two, both mean \"no profile here\".")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        UUID identityId;
        try {
            identityId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new UsernameNotFoundException("Invalid or unknown identity");
        }
        return ResponseEntity.ok(userService.getCurrentUser(identityId));
    }

    @PatchMapping("/me")
    @Tag(name = OpenApiConfig.TAG_SELF_SERVICE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update the caller's own profile", description = "Requires a Bearer token "
            + "backed by a real Identity. Partial update -- a null field in the request body is left "
            + "unchanged. Does not accept mobileNumber (tied to OTP verification, changing it here would "
            + "be an account-takeover risk) or a password (use the existing password-change flow).")
    public ResponseEntity<UserResponse> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                          @AuthenticationPrincipal Jwt jwt) {
        UUID identityId;
        try {
            identityId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new UsernameNotFoundException("Invalid or unknown identity");
        }
        return ResponseEntity.ok(userService.updateMyProfile(identityId, request));
    }

    @GetMapping("/{id}")
    @Tag(name = OpenApiConfig.TAG_BUSINESS_SERVICE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Look up a user's profile by id", description = "Requires any valid Bearer "
            + "token (client_credentials or a user's own PKCE-issued token) -- not PLATFORM_ADMIN-gated. "
            + "Intended for a business service to resolve a user id it already has (e.g. from a "
            + "product listing's owner field) into a display name/email.")
    public ResponseEntity<UserResponse> getUserById(@PathVariable("id") Long userId) {
        UserResponse userResponse = userService.getUserById(userId);
        return ResponseEntity.ok(userResponse);
    }

    @GetMapping
    @Tag(name = OpenApiConfig.TAG_PLATFORM_ADMIN)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List all users", description = "PLATFORM_ADMIN only.")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PatchMapping("/{id}/platform-admin")
    @Tag(name = OpenApiConfig.TAG_PLATFORM_ADMIN)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Promote or demote a PLATFORM_ADMIN", description = "PLATFORM_ADMIN only.")
    public ResponseEntity<UserResponse> setPlatformAdmin(@PathVariable("id") Long userId,
                                                           @Valid @RequestBody PlatformAdminRequest request) {
        return ResponseEntity.ok(userService.setPlatformAdmin(userId, request.getPlatformAdmin()));
    }

    @DeleteMapping("/{id}/pin-devices/{deviceId}")
    @Tag(name = OpenApiConfig.TAG_PLATFORM_ADMIN)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Revoke a user's device PIN", description = "PLATFORM_ADMIN only. Kills a "
            + "specific device's PIN login centrally (e.g. a lost/stolen phone), independent of "
            + "whether the device itself is reachable.")
    public ResponseEntity<Void> revokePinDevice(@PathVariable("id") Long userId,
                                                 @PathVariable("deviceId") String deviceId) {
        userService.revokePinDevice(userId, deviceId);
        return ResponseEntity.noContent().build();
    }
}
