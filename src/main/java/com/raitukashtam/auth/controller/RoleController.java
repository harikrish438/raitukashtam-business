package com.raitukashtam.auth.controller;

import com.raitukashtam.auth.request.RoleRequest;
import com.raitukashtam.auth.response.RoleResponse;
import com.raitukashtam.auth.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products/{productCode}/roles")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roleService;

    @PostMapping
    public ResponseEntity<RoleResponse> createRole(@PathVariable String productCode,
                                                     @Valid @RequestBody RoleRequest request) {
        return new ResponseEntity<>(roleService.createRole(productCode, request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<RoleResponse>> listRoles(@PathVariable String productCode) {
        return ResponseEntity.ok(roleService.listRoles(productCode));
    }
}
