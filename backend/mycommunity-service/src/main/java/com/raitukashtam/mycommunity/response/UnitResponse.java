package com.raitukashtam.mycommunity.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnitResponse {
    private Long id;
    private Long communityId;
    private String unitNumber;
    private String block;
    private Integer floor;
    private BigDecimal areaSqft;
    private String unitType;
    private boolean active;
    private LocalDateTime createdAt;
}
