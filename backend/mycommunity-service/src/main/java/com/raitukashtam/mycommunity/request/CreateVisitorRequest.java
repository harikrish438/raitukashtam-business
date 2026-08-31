package com.raitukashtam.mycommunity.request;

import com.raitukashtam.mycommunity.entity.VisitorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateVisitorRequest {
    @NotBlank(message = "Guest name is required")
    private String guestName;

    @NotNull(message = "Visitor type is required")
    private VisitorType type;

    private String purpose;

    /** If true, creates already CHECKED_IN with entryTime=now (a walk-in logged after the fact) instead of EXPECTED (a pre-approval). */
    private boolean checkedInNow;
}
