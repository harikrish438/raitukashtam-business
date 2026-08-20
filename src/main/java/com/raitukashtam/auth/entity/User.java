package com.raitukashtam.auth.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.UuidGenerator;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "app_user")
@Data
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_code", referencedColumnName = "code")
    private Tenant tenant;

    @Column(nullable = false)
    private boolean verified = false; // Default to false

    @Column(unique = true)
    private String mobileNumber;

    @Column(name = "is_locked", nullable = false, columnDefinition = "boolean default false")
    private boolean isLocked = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identity_id")
    private Identity identity;
}
