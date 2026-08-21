package com.raitukashtam.auth.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlatformAdminRequest {
    @NotNull(message = "platformAdmin is required")
    private Boolean platformAdmin;
}
