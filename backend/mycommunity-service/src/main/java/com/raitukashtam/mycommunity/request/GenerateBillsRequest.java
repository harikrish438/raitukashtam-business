package com.raitukashtam.mycommunity.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class GenerateBillsRequest {
    @NotBlank(message = "Period is required")
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Period must be in YYYY-MM format")
    private String period;

    /** Required when the community's billingMode is FLAT; must be omitted when PER_AREA (the amount is computed per member instead) -- see BillService.generateBills. */
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Due date is required")
    private LocalDate dueDate;
}
