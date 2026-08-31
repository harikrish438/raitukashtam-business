package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.config.OpenApiConfig;
import com.raitukashtam.auth.repository.IdentityRepository;
import com.raitukashtam.auth.request.ProductRequest;
import com.raitukashtam.auth.response.ProductResponse;
import com.raitukashtam.auth.service.ProductService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_PLATFORM_ADMIN)
@SecurityRequirement(name = "bearerAuth")
public class ProductController {
    private final ProductService productService;
    private final IdentityRepository identityRepository;

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request,
                                                           @AuthenticationPrincipal Jwt jwt) {
        return new ResponseEntity<>(productService.createProduct(request, resolveCreatedBy(jwt)), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @GetMapping("/{code}")
    public ResponseEntity<ProductResponse> getProductByCode(@PathVariable String code) {
        return ResponseEntity.ok(productService.getProductByCode(code));
    }

    /**
     * The JWT subject is the caller's Identity UUID (never changed -- see
     * the JWT sub claim discussion), but an audit column is more useful to
     * a human reading it as an email, matching every self-service creation
     * path (UserService.registerUser et al.) which already store the
     * registering user's email in createdBy, not a UUID. Falls back to the
     * raw UUID if the identity lookup somehow fails, rather than blocking
     * creation over an audit-field resolution issue.
     */
    private String resolveCreatedBy(Jwt jwt) {
        return identityRepository.findById(UUID.fromString(jwt.getSubject()))
                .map(identity -> identity.getPrimaryEmail())
                .orElse(jwt.getSubject());
    }
}
