package com.raitukashtam.auth.repository;

import com.raitukashtam.auth.entity.ProductMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductMembershipRepository extends JpaRepository<ProductMembership, Long> {
    boolean existsByUser_IdAndProduct_Code(Long userId, String productCode);
    List<ProductMembership> findByUser_Id(Long userId);
}
