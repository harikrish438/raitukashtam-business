package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Metadata only -- the actual file bytes live in S3 under s3Key, not in
 * this table. Named CommunityDocument (not Document) to avoid colliding
 * with java.lang and Lucene-adjacent "Document" names elsewhere.
 */
@Entity
@Table(name = "community_document")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityDocument extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Column(nullable = false)
    private String title;

    private String description;

    /** Free text, not an enum -- same open-ended reasoning as Expense.category (Rules, Minutes, Notices, AGM, ...). */
    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'ALL_MEMBERS'")
    private DocumentVisibility visibility = DocumentVisibility.ALL_MEMBERS;

    /** The S3 object key -- never exposed in API responses, only used server-side to fetch/delete the file. */
    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_member_id", nullable = false)
    private CommunityMember uploadedBy;
}
