package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Entity
@Table(name = "amenity_booking")
@Data
@EqualsAndHashCode(callSuper = true)
public class AmenityBooking extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "amenity_id", nullable = false)
    private Amenity amenity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private CommunityMember member;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    /** Free-text slot label, e.g. "18:00-19:00" -- not a structured time range in this phase. */
    @Column(nullable = false)
    private String slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'PENDING'")
    private AmenityBookingStatus status = AmenityBookingStatus.PENDING;
}
