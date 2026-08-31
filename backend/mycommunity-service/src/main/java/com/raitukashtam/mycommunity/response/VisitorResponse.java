package com.raitukashtam.mycommunity.response;

import com.raitukashtam.mycommunity.entity.VisitorStatus;
import com.raitukashtam.mycommunity.entity.VisitorType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VisitorResponse {
    private Long id;
    private Long communityId;
    private Long hostMemberId;
    private String hostName;
    private String guestName;
    private VisitorType type;
    private String purpose;
    private VisitorStatus status;
    private LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private LocalDateTime createdAt;
}
