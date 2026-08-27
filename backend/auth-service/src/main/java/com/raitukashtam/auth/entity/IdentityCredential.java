package com.raitukashtam.auth.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@Table(name = "identity_credential",
        uniqueConstraints = @UniqueConstraint(columnNames = {"credential_type", "external_subject"}))
public class IdentityCredential extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identity_id", nullable = false)
    private Identity identity;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false)
    private CredentialType credentialType;

    @Column(name = "external_subject")
    private String externalSubject;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private boolean verified = false;
}
