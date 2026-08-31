package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "community_join_request")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityJoinRequest extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    /** The auth-service Identity UUID of the caller who requested to join. */
    @Column(name = "requester_identity_id", nullable = false)
    private String requesterIdentityId;

    @Column(name = "requester_mobile_number", nullable = false)
    private String requesterMobileNumber;

    @Column(name = "requester_name", nullable = false)
    private String requesterName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'PENDING'")
    private JoinRequestStatus status = JoinRequestStatus.PENDING;
}
