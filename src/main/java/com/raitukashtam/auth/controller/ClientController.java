package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.request.ClientRequest;
import com.raitukashtam.auth.response.ClientResponse;
import com.raitukashtam.auth.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products/{productCode}/clients")
@RequiredArgsConstructor
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
