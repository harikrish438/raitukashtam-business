package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bill",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "period"}))
@Data
@EqualsAndHashCode(callSuper = true)
public class Bill extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private CommunityMember member;

    /** Billing period, "YYYY-MM" -- see BillService for validation. */
    @Column(nullable = false)
    private String period;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'PENDING'")
    private BillStatus status = BillStatus.PENDING;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
