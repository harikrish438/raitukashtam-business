package com.raitukashtam.auth.repository;

import com.raitukashtam.auth.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByClientId(String clientId);
    boolean existsByClientId(String clientId);
}
