package com.raitukashtam.mycommunity.request;

import com.raitukashtam.mycommunity.entity.StaffRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StaffRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @NotNull(message = "Role is required")
    private StaffRole role;

    private String phoneNumber;
}
