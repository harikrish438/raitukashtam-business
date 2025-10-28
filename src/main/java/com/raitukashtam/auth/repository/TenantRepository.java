package com.raitukashtam.auth.repository;

import com.raitukashtam.auth.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    boolean existsByCode(String code);
    Optional<Tenant> findByCode(String code);
}
