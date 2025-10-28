package com.raitukashtam.auth.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TenantRequest {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Code is required")
    @Pattern(regexp = "^[A-Z0-9-]+$", message = "Code can only contain uppercase letters, numbers, and hyphens")
    @Size(min = 3, max = 50, message = "Code must be between 3 and 50 characters")
    private String code;
    private String region;
    private String pincode;
}
