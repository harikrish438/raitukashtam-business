package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
}
