package com.raitukashtam.mycommunity.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementResponse {
    private Long id;
    private Long communityId;
    private String title;
    private String body;
    private Long postedByMemberId;
    private String postedByName;
    private LocalDateTime createdAt;
}
