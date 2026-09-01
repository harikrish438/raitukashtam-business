package com.raitukashtam.mycommunity.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignUnitRequest {
    @NotNull(message = "Unit id is required")
    private Long unitId;
}
