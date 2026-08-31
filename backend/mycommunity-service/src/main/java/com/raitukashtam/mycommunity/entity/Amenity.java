package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Entity
@Table(name = "amenity")
@Data
@EqualsAndHashCode(callSuper = true)
public class Amenity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "is_paid", nullable = false)
    private boolean paid;

    /** Only meaningful when paid=true -- informational only, no payment collection wired up in this phase. */
    @Column(precision = 12, scale = 2)
    private BigDecimal fee;

    private String rules;

    /** Soft-retire flag -- an Amenity with booking history can't be hard-deleted without breaking that history. */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;
}
