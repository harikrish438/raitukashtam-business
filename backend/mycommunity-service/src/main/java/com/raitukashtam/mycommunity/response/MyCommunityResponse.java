package com.raitukashtam.mycommunity.response;

import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyCommunityResponse {
    private Long communityId;
    private String communityName;
    private CommunityRole role;
    private MemberStatus status;
}
