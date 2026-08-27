package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.config.OpenApiConfig;
import com.raitukashtam.auth.entity.User;
import com.raitukashtam.auth.request.PlatformAdminRequest;
import com.raitukashtam.auth.request.RegisterRequest;
import com.raitukashtam.auth.response.UserResponse;
import com.raitukashtam.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
