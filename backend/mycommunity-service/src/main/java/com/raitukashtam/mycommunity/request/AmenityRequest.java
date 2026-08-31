package com.raitukashtam.mycommunity.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AmenityRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    private String description;

    private boolean paid;

    /** Required when paid=true -- validated in AmenityService, not here, since it's conditional. */
    @DecimalMin(value = "0.01", message = "Fee must be greater than zero")
    private BigDecimal fee;

    private String rules;
}
