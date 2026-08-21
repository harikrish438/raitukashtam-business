package com.raitukashtam.auth.service;

import com.raitukashtam.auth.entity.Product;
import com.raitukashtam.auth.entity.ProductMembership;
import com.raitukashtam.auth.entity.Role;
import com.raitukashtam.auth.entity.RoleAssignment;
import com.raitukashtam.auth.exception.ResourceAlreadyExistsException;
import com.raitukashtam.auth.exception.ResourceNotFoundException;
import com.raitukashtam.auth.repository.ProductRepository;
import com.raitukashtam.auth.repository.RoleAssignmentRepository;
import com.raitukashtam.auth.repository.RoleRepository;
import com.raitukashtam.auth.request.RoleRequest;
import com.raitukashtam.auth.response.RoleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private static final String DEFAULT_ROLE_CODE = "CONSUMER";

    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final ProductRepository productRepository;

    /**
     * Assigns the default (CONSUMER) role for a brand-new membership.
     * The role code is always this hardcoded default, never client-supplied
     * -- registration/Google provisioning must not let a caller self-assign
     * a privileged role.
     */
    @Transactional
    public void assignDefaultRole(ProductMembership membership) {
        Role defaultRole = roleRepository
                .findByProduct_CodeAndCode(membership.getProduct().getCode(), DEFAULT_ROLE_CODE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Default role '" + DEFAULT_ROLE_CODE + "' not found for product: " + membership.getProduct().getCode()));

        RoleAssignment assignment = new RoleAssignment();
        assignment.setProductMembership(membership);
        assignment.setRole(defaultRole);
        roleAssignmentRepository.save(assignment);
    }

    @Transactional(readOnly = true)
    public List<String> getRoleCodes(ProductMembership membership) {
        return roleAssignmentRepository.findByProductMembership_Id(membership.getId()).stream()
                .map(assignment -> assignment.getRole().getCode())
                .toList();
    }

    @Transactional
    public RoleResponse createRole(String productCode, RoleRequest request) {
        Product product = productRepository.findByCode(productCode)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with code: " + productCode));

        if (roleRepository.findByProduct_CodeAndCode(productCode, request.getCode()).isPresent()) {
            throw new ResourceAlreadyExistsException(
                    "Role with code " + request.getCode() + " already exists for product " + productCode);
        }

        Role role = new Role();
        role.setProduct(product);
        role.setCode(request.getCode());
        role.setName(request.getName());

        Role saved = roleRepository.save(role);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles(String productCode) {
        if (!productRepository.existsByCode(productCode)) {
            throw new ResourceNotFoundException("Product not found with code: " + productCode);
        }
        return roleRepository.findByProduct_Code(productCode).stream()
                .map(this::toResponse)
                .toList();
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getProduct().getCode(), role.getCode(), role.getName(), role.getCreatedAt());
    }
}
