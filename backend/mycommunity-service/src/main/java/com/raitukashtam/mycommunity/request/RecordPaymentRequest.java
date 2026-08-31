package com.raitukashtam.mycommunity.request;

import com.raitukashtam.mycommunity.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RecordPaymentRequest {
    @NotNull(message = "Payment method is required")
    private PaymentMethod method;

    @Size(max = 255, message = "Reference must be at most 255 characters")
    private String reference;

    /** Optional -- defaults to now. Lets an admin back-date a payment that happened earlier but is only now being recorded. */
    private LocalDateTime paidAt;
}
