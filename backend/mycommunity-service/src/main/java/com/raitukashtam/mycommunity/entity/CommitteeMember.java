package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Entity
@Table(name = "committee_member")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommitteeMember extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    /** The underlying community membership (ADMIN or RESIDENT) this committee seat is layered on -- committee membership grants no extra authorization in this phase, it's directory/term data only. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private CommunityMember member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommitteePosition position;

    /** Required only when position=OTHER. */
    @Column(name = "custom_position")
    private String customPosition;

    @Column(name = "term_start", nullable = false)
    private LocalDate termStart;

    /** Null means the term is still current/ongoing. */
    @Column(name = "term_end")
    private LocalDate termEnd;
}
