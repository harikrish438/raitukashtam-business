package com.raitukashtam.auth.repository;

import com.raitukashtam.auth.entity.CredentialType;
import com.raitukashtam.auth.entity.IdentityCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IdentityCredentialRepository extends JpaRepository<IdentityCredential, Long> {
    Optional<IdentityCredential> findByCredentialTypeAndExternalSubject(CredentialType credentialType, String externalSubject);
    Optional<IdentityCredential> findByIdentity_IdAndCredentialType(UUID identityId, CredentialType credentialType);

    // DEVICE_PIN is the one credential type an identity can hold several of
    // (one per device) -- the singular findByIdentity_IdAndCredentialType
    // above is for the always-at-most-one types (PASSWORD, GOOGLE, ...).
    List<IdentityCredential> findAllByIdentity_IdAndCredentialType(UUID identityId, CredentialType credentialType);
}
