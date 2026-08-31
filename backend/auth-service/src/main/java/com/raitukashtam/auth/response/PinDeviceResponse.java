package com.raitukashtam.auth.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Never carries the PIN hash -- deviceId/verified/createdAt only. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PinDeviceResponse {
    private String deviceId;
    private boolean verified;
    private LocalDateTime createdAt;
}
