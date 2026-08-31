package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One full payment per Bill -- no partial payments in v1. amount is
 * copied from the Bill at recording time (kept on Payment too so a
 * receipt reads correctly even if the Bill itself were ever adjusted).
 */
@Entity
@Table(name = "payment", uniqueConstraints = @UniqueConstraint(columnNames = {"bill_id"}))
@Data
@EqualsAndHashCode(callSuper = true)
public class Payment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    /** Transaction id / cheque number / UPI ref -- optional, method-dependent. */
    private String reference;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_member_id", nullable = false)
    private CommunityMember recordedBy;
}
