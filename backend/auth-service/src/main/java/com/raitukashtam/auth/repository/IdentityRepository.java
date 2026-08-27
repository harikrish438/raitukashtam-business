package com.raitukashtam.auth.repository;

import com.raitukashtam.auth.entity.Identity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdentityRepository extends JpaRepository<Identity, UUID> {
    Optional<Identity> findByPrimaryEmail(String primaryEmail);
    boolean existsByPrimaryEmail(String primaryEmail);
}
