package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.config.OpenApiConfig;
import com.raitukashtam.auth.repository.IdentityRepository;
import com.raitukashtam.auth.request.ClientRequest;
import com.raitukashtam.auth.response.ClientResponse;
import com.raitukashtam.auth.service.ClientService;
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
@RequestMapping("/products/{productCode}/clients")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_PLATFORM_ADMIN)
@SecurityRequirement(name = "bearerAuth")
public class ClientController {
    private final ClientService clientService;
    private final IdentityRepository identityRepository;

    @PostMapping
    public ResponseEntity<ClientResponse> createClient(@PathVariable String productCode,
                                                         @Valid @RequestBody ClientRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        return new ResponseEntity<>(clientService.createClient(productCode, request, resolveCreatedBy(jwt)), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ClientResponse>> listClients(@PathVariable String productCode) {
        return ResponseEntity.ok(clientService.listClients(productCode));
    }

    /** See ProductController.resolveCreatedBy for why this resolves to an email, not the raw JWT subject UUID. */
    private String resolveCreatedBy(Jwt jwt) {
        return identityRepository.findById(UUID.fromString(jwt.getSubject()))
                .map(identity -> identity.getPrimaryEmail())
                .orElse(jwt.getSubject());
    }
}
