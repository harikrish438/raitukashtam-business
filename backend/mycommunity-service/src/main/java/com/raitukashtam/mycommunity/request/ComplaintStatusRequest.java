package com.raitukashtam.mycommunity.request;

import com.raitukashtam.mycommunity.entity.ComplaintStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ComplaintStatusRequest {
    @NotNull(message = "Status is required")
    private ComplaintStatus status;
}
