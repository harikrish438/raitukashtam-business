package com.raitukashtam.auth.repository;

import com.raitukashtam.auth.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByMobileNumber(String mobileNumber);
    Optional<User> findByIdentity_Id(UUID identityId);

    @EntityGraph(attributePaths = {"tenant"})
    Optional<User> findWithTenantById(Long id);

    @Query("SELECT u FROM User u JOIN FETCH u.tenant t WHERE t.code = :tenantCode")
    List<User> findAllByTenantCode(@Param("tenantCode") String tenantCode);
}
