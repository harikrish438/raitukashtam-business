package com.raitukashtam.mycommunity.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponse {
    private Long id;
    private Long communityId;
    private String category;
    private String description;
    private BigDecimal amount;
    private LocalDate expenseDate;
    private Long createdByMemberId;
    private String createdByName;
    private LocalDateTime createdAt;
    private Long vendorId;
    private String vendorName;
}
