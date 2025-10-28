package com.raitukashtam.auth.service;

import com.raitukashtam.auth.entity.Tenant;
import com.raitukashtam.auth.exception.ResourceAlreadyExistsException;
import com.raitukashtam.auth.exception.ResourceNotFoundException;
import com.raitukashtam.auth.repository.TenantRepository;
import com.raitukashtam.auth.request.TenantRequest;
import com.raitukashtam.auth.response.TenantResponse;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TenantService {
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private ModelMapper modelMapper;

    @Transactional
    public TenantResponse createTenant(TenantRequest request) {
        // Check if tenant with same code already exists
        if (tenantRepository.existsByCode(request.getCode())) {
            throw new ResourceAlreadyExistsException("Tenant with code " + request.getCode() + " already exists");
        }

        // Create and save new tenant
        Tenant tenant = new Tenant();
        tenant.setName(request.getName());
        tenant.setCode(request.getCode());
        tenant.setRegion(request.getRegion());
        tenant.setPincode(request.getPincode());

        Tenant savedTenant = tenantRepository.save(tenant);
        return convertToDto(savedTenant);
    }

    public List<TenantResponse> getAllTenants() {
        return tenantRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public TenantResponse getTenantByCode(String code) {
        return tenantRepository.findByCode(code.toLowerCase())
                .map(this::convertToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with code: " + code));
    }

    private TenantResponse convertToDto(Tenant tenant) {
        return modelMapper.map(tenant, TenantResponse.class);
    }
}
