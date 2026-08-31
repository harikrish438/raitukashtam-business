package com.raitukashtam.mycommunity.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityItemResponse {
    private ActivityType type;
    private String title;
    /** Only set for PAYMENT items. */
    private BigDecimal amount;
    private String actorName;
    private LocalDateTime occurredAt;
}
