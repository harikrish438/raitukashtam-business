package com.raitukashtam.mycommunity.response;

import com.raitukashtam.mycommunity.entity.DevicePlatform;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** fcmToken is deliberately never included -- internal only, same reasoning as CommunityDocument.s3Key. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenResponse {
    private Long id;
    private String deviceId;
    private DevicePlatform platform;
    private LocalDateTime createdAt;
}
