package com.raitukashtam.mycommunity.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "device_token",
        uniqueConstraints = @UniqueConstraint(columnNames = {"identity_id", "device_id"}))
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceToken extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The auth-service Identity UUID (JWT sub) -- not community-scoped, since one person's device works across every community they belong to. */
    @Column(name = "identity_id", nullable = false)
    private String identityId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    /** The FCM registration token -- rotates over the device's lifetime, so registering with an existing deviceId overwrites this rather than creating a new row. */
    @Column(name = "fcm_token", nullable = false, length = 4096)
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DevicePlatform platform;
}
