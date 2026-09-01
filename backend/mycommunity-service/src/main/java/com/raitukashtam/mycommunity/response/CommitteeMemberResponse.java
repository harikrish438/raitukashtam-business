package com.raitukashtam.mycommunity.response;

import com.raitukashtam.mycommunity.entity.CommitteePosition;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommitteeMemberResponse {
    private Long id;
    private Long communityId;
    private Long memberId;
    private String memberName;
    private CommitteePosition position;
    private String customPosition;
    private LocalDate termStart;
    private LocalDate termEnd;
    private boolean current;
}
