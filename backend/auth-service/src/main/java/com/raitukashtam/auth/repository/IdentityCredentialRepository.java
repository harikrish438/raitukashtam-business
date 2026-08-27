package com.raitukashtam.auth.repository;

import com.raitukashtam.auth.entity.CredentialType;
import com.raitukashtam.auth.entity.IdentityCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdentityCredentialRepository extends JpaRepository<IdentityCredential, Long> {
    Optional<IdentityCredential> findByCredentialTypeAndExternalSubject(CredentialType credentialType, String externalSubject);
    Optional<IdentityCredential> findByIdentity_IdAndCredentialType(UUID identityId, CredentialType credentialType);
}
