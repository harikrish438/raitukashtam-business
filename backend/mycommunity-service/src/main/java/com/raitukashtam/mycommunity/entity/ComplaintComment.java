package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "complaint_comment")
@Data
@EqualsAndHashCode(callSuper = true)
public class ComplaintComment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_member_id", nullable = false)
    private CommunityMember author;

    @Column(nullable = false, columnDefinition = "text")
    private String comment;
}
