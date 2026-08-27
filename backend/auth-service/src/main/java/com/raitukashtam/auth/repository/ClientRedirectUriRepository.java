package com.raitukashtam.auth.repository;

import com.raitukashtam.auth.entity.ClientRedirectUri;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientRedirectUriRepository extends JpaRepository<ClientRedirectUri, Long> {
    List<ClientRedirectUri> findByClient_Id(Long clientId);
}
