package com.raitukashtam.mycommunity.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignComplaintRequest {
    @NotNull(message = "Assignee member id is required")
    private Long assigneeMemberId;
}
