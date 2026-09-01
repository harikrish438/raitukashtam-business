package com.raitukashtam.mycommunity.response;

import com.raitukashtam.mycommunity.entity.BillingMode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommunityResponse {
    private Long id;
    private String name;
    private Integer totalUnits;
    private String street;
    private String area;
    private String district;
    private String state;
    private String pincode;
    private String landmark;
    private BillingMode billingMode;
    private BigDecimal ratePerSqft;
    private LocalDateTime createdAt;
}
