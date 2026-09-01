package com.raitukashtam.mycommunity.request;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

/** Partial update -- a null field is left unchanged, a blank unitNumber is rejected (see UnitService.updateUnit). */
@Data
public class UnitUpdateRequest {
    private String unitNumber;

    private String block;

    private Integer floor;

    @DecimalMin(value = "0.01", message = "Area must be greater than zero")
    private BigDecimal areaSqft;

    private String unitType;
}
