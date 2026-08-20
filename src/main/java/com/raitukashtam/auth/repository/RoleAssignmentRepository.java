package com.raitukashtam.auth.repository;

import com.raitukashtam.auth.entity.RoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, Long> {
    List<RoleAssignment> findByProductMembership_Id(Long productMembershipId);
}
