package com.raitukashtam.mycommunity.response;

import com.raitukashtam.mycommunity.entity.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private Long communityId;
    private Long billId;
    private String billPeriod;
    private Long memberId;
    private String memberName;
    private BigDecimal amount;
    private PaymentMethod method;
    private String reference;
    private LocalDateTime paidAt;
    private Long recordedByMemberId;
    private String recordedByName;
}
