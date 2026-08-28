package com.raitukashtam.mycommunity.response;

import com.raitukashtam.mycommunity.entity.CommunityRole;
import com.raitukashtam.mycommunity.entity.MemberStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommunityMemberResponse {
    private Long id;
    private Long communityId;
    private String name;
    private String unitNumber;
    private String mobileNumber;
    private CommunityRole role;
    private MemberStatus status;
    private LocalDateTime createdAt;
}
