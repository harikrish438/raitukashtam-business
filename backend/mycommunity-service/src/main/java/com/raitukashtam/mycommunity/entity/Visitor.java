package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Entity
@Table(name = "visitor")
@Data
@EqualsAndHashCode(callSuper = true)
public class Visitor extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    /** The resident who invited/is hosting this visitor. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_member_id", nullable = false)
    private CommunityMember host;

    @Column(name = "guest_name", nullable = false)
    private String guestName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VisitorType type;

    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'EXPECTED'")
    private VisitorStatus status = VisitorStatus.EXPECTED;

    @Column(name = "entry_time")
    private LocalDateTime entryTime;

    @Column(name = "exit_time")
    private LocalDateTime exitTime;
}
