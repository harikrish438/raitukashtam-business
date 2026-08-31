package com.raitukashtam.mycommunity.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AmenityResponse {
    private Long id;
    private Long communityId;
    private String name;
    private String description;
    private boolean paid;
    private BigDecimal fee;
    private String rules;
    private boolean active;
}
