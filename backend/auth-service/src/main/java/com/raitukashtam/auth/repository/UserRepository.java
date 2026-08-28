package com.raitukashtam.auth.repository;

import com.raitukashtam.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByMobileNumber(String mobileNumber);
    Optional<User> findByMobileNumber(String mobileNumber);
    Optional<User> findByIdentity_Id(UUID identityId);
}
