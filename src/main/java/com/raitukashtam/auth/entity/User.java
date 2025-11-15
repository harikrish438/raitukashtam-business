package com.raitukashtam.auth.entity;

import com.raitukashtam.auth.model.UserRole;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.UuidGenerator;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "users")
@Data
public class User extends BaseEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String password; // Will be hashed

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'CONSUMER'")
    private UserRole role = UserRole.CONSUMER; // Default role

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_code", referencedColumnName = "code", insertable = false, updatable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private boolean verified = false; // Default to false

    @Column(unique = true, nullable = false)
    private String mobileNumber;

    @Column(name = "is_locked", nullable = false, columnDefinition = "boolean default false")
    private boolean isLocked = false;
}
