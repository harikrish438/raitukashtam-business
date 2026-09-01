package com.raitukashtam.mycommunity.request;

import com.raitukashtam.mycommunity.entity.BillingMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** ratePerSqft is required only when billingMode=PER_AREA (validated in CommunityService.updateBillingSettings, not declaratively -- it's conditional on another field). */
@Data
public class BillingSettingsRequest {
    @NotNull(message = "Billing mode is required")
    private BillingMode billingMode;

    @DecimalMin(value = "0.01", message = "Rate per sqft must be greater than zero")
    private BigDecimal ratePerSqft;
}
