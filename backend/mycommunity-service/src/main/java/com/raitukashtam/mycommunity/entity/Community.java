package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Entity
@Table(name = "community")
@Data
@EqualsAndHashCode(callSuper = true)
public class Community extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "total_units", nullable = false)
    private Integer totalUnits;

    @Column(nullable = false)
    private String street;

    @Column(nullable = false)
    private String area;

    @Column(nullable = false)
    private String district;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String pincode;

    private String landmark;

    /** FLAT (default -- practical for most communities) applies the generate-request's flat amount to every member; PER_AREA computes each member's bill from ratePerSqft x their unit's areaSqft. */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_mode", nullable = false, columnDefinition = "varchar(255) default 'FLAT'")
    private BillingMode billingMode = BillingMode.FLAT;

    /** Only meaningful when billingMode=PER_AREA. */
    @Column(name = "rate_per_sqft", precision = 10, scale = 2)
    private BigDecimal ratePerSqft;
}
