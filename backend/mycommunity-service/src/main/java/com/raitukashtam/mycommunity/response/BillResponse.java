package com.raitukashtam.mycommunity.response;

import com.raitukashtam.mycommunity.entity.BillStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillResponse {
    private Long id;
    private Long communityId;
    private Long memberId;
    private String memberName;
    private String unitNumber;
    private String period;
    private BigDecimal amount;
    private BillStatus status;
    private LocalDate dueDate;
    private LocalDateTime paidAt;
}
