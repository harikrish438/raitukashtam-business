package com.raitukashtam.auth.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Entity
@Data
public class Tenant extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "tenant", fetch = FetchType.LAZY)
    private Set<User> users = new HashSet<>();

    @Column(unique = true, nullable = false)
    private String code;  // Unique identifier for the tenant (e.g., "acme-corp")

    @Column
    private String region;

    @Column
    private String pincode;// e.g., "us-east", "eu-west", etc.

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
}
