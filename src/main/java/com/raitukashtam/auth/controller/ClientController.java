package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.config.OpenApiConfig;
import com.raitukashtam.auth.request.ClientRequest;
import com.raitukashtam.auth.response.ClientResponse;
import com.raitukashtam.auth.service.ClientService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products/{productCode}/clients")
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_PLATFORM_ADMIN)
@SecurityRequirement(name = "bearerAuth")
public class ClientController {
    private final ClientService clientService;

    @PostMapping
    public ResponseEntity<ClientResponse> createClient(@PathVariable String productCode,
                                                         @Valid @RequestBody ClientRequest request) {
        return new ResponseEntity<>(clientService.createClient(productCode, request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ClientResponse>> listClients(@PathVariable String productCode) {
        return ResponseEntity.ok(clientService.listClients(productCode));
    }
}
