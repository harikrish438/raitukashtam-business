package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "community_member",
        uniqueConstraints = @UniqueConstraint(columnNames = {"community_id", "mobile_number"}))
@Data
@EqualsAndHashCode(callSuper = true)
public class CommunityMember extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @Column(nullable = false)
    private String name;

    @Column(name = "unit_number", nullable = false)
    private String unitNumber;

    @Column(name = "mobile_number", nullable = false)
    private String mobileNumber;

    /** Optional -- set by the member themselves via the self-service profile update, never at invite time. */
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommunityRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'INVITED'")
    private MemberStatus status = MemberStatus.INVITED;

    /** The auth-service Identity UUID, populated once this mobile number completes a real login. Null until then. */
    @Column(name = "identity_id")
    private String identityId;
}
