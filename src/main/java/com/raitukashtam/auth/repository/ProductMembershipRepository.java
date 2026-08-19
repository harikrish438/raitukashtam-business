package com.raitukashtam.auth.repository;

import com.raitukashtam.auth.entity.ProductMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductMembershipRepository extends JpaRepository<ProductMembership, Long> {
    boolean existsByIdentity_IdAndProduct_Code(UUID identityId, String productCode);
    List<ProductMembership> findByIdentity_Id(UUID identityId);
}
