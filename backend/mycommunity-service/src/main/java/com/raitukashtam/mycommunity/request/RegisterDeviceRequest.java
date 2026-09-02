package com.raitukashtam.mycommunity.request;

import com.raitukashtam.mycommunity.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RegisterDeviceRequest {
    @NotBlank(message = "Device id is required")
    private String deviceId;

    @NotBlank(message = "FCM token is required")
    private String fcmToken;

    @NotNull(message = "Platform is required")
    private DevicePlatform platform;
}
