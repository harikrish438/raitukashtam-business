package com.raitukashtam.mycommunity.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinRequestRequest {
    @NotBlank(message = "Name is required")
    private String name;
}
