package com.raitukashtam.auth.repository;

import com.raitukashtam.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByProduct_CodeAndCode(String productCode, String code);
    List<Role> findByProduct_Code(String productCode);
}
