package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Entity
@Table(name = "unit",
        uniqueConstraints = @UniqueConstraint(columnNames = {"community_id", "unit_number"}))
@Data
@EqualsAndHashCode(callSuper = true)
public class Unit extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Column(name = "unit_number", nullable = false)
    private String unitNumber;

    private String block;

    private Integer floor;

    /** Nullable -- required only once a community switches to PER_AREA billing (checked at bill-generation time, not here). */
    @Column(name = "area_sqft", precision = 10, scale = 2)
    private BigDecimal areaSqft;

    @Column(name = "unit_type")
    private String unitType;

    /** Soft-retire flag -- a Unit with member/bill history can't be hard-deleted without breaking it. */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;
}
