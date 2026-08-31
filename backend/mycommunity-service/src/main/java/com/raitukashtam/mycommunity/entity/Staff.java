package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "staff")
@Data
@EqualsAndHashCode(callSuper = true)
public class Staff extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffRole role;

    @Column(name = "phone_number")
    private String phoneNumber;

    /** Soft-retire flag -- Staff with attendance history can't be hard-deleted without breaking that history. */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;
}
