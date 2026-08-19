package com.raitukashtam.auth.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Entity
@Data
public class Identity extends BaseEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "primary_email", unique = true, nullable = false)
    private String primaryEmail;

    @Column(name = "primary_phone")
    private String primaryPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'ACTIVE'")
    private IdentityStatus status = IdentityStatus.ACTIVE;
}
