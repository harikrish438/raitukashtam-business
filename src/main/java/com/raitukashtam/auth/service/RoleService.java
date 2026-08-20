package com.raitukashtam.auth.service;

import com.raitukashtam.auth.entity.ProductMembership;
import com.raitukashtam.auth.entity.Role;
import com.raitukashtam.auth.entity.RoleAssignment;
import com.raitukashtam.auth.exception.ResourceNotFoundException;
import com.raitukashtam.auth.repository.RoleAssignmentRepository;
import com.raitukashtam.auth.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleService {

    private static final String DEFAULT_ROLE_CODE = "CONSUMER";

    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

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
}
