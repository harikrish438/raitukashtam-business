package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "complaint")
@Data
@EqualsAndHashCode(callSuper = true)
public class Complaint extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raised_by_member_id", nullable = false)
    private CommunityMember raisedBy;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'MEDIUM'")
    private ComplaintPriority priority = ComplaintPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'OPEN'")
    private ComplaintStatus status = ComplaintStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_member_id")
    private CommunityMember assignedTo;
}
