package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * A persisted record of a push notification, one row per recipient identity --
 * written even when Firebase isn't configured or the identity has no device
 * token, so the in-app notification history is never lossy. Identity-scoped
 * like DeviceToken (recipientIdentityId), but each row also carries the
 * community it's about since a person's history spans every community they
 * belong to.
 */
@Entity
@Table(name = "notification")
@Data
@EqualsAndHashCode(callSuper = true)
public class Notification extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "community_id", nullable = false)
    private Long communityId;

    /** Denormalized at write time so listing history never needs a join. */
    @Column(name = "community_name", nullable = false)
    private String communityName;

    @Column(name = "recipient_identity_id", nullable = false)
    private String recipientIdentityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    /** The id of the thing this notification is about (a complaint, bill, ...) -- null when there's nothing to deep-link to. */
    @Column(name = "reference_id")
    private Long referenceId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(nullable = false)
    private boolean read = false;
}
