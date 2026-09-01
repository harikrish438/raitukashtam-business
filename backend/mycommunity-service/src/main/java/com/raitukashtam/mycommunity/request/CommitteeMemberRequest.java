package com.raitukashtam.mycommunity.request;

import com.raitukashtam.mycommunity.entity.CommitteePosition;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CommitteeMemberRequest {
    @NotNull(message = "Member id is required")
    private Long memberId;

    @NotNull(message = "Position is required")
    private CommitteePosition position;

    /** Required only when position=OTHER -- see CommitteeService.createCommitteeMember. */
    private String customPosition;

    /** Defaults to today when omitted. */
    private LocalDate termStart;
}
