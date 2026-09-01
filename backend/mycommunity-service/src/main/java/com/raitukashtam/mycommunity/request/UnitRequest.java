package com.raitukashtam.mycommunity.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UnitRequest {
    @NotBlank(message = "Unit number is required")
    private String unitNumber;

    private String block;

    private Integer floor;

    /** Optional at creation -- required only once the community switches to PER_AREA billing (validated at bill-generation time). */
    @DecimalMin(value = "0.01", message = "Area must be greater than zero")
    private BigDecimal areaSqft;

    private String unitType;
}
