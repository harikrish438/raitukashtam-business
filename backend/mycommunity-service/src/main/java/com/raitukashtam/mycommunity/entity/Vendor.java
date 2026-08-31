package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "vendor")
@Data
@EqualsAndHashCode(callSuper = true)
public class Vendor extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Column(nullable = false)
    private String name;

    /** Free text, not an enum -- as open-ended as Expense.category (electrician, pest control, lift AMC, ...). */
    @Column(name = "service_type", nullable = false)
    private String serviceType;

    @Column(name = "contact_person")
    private String contactPerson;

    @Column(name = "phone_number")
    private String phoneNumber;

    /** Soft-retire flag -- a Vendor with linked Expenses can't be hard-deleted without breaking that history. */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;
}
