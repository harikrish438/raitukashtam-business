package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.request.TenantRequest;
import com.raitukashtam.auth.response.TenantResponse;
import com.raitukashtam.auth.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantController {
    private final TenantService tenantService;

    @PostMapping("/create")
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody TenantRequest request) {
        TenantResponse response = tenantService.createTenant(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/getAll")
    public ResponseEntity<List<TenantResponse>> getAllTenants() {
        return ResponseEntity.ok(tenantService.getAllTenants());
    }

    @GetMapping("/getByCode/{code}")
    public ResponseEntity<TenantResponse> getTenantByCode(@PathVariable String code) {
        return ResponseEntity.ok(tenantService.getTenantByCode(code));
    }
}
