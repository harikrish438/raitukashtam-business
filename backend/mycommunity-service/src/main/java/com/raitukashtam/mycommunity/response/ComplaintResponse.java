package com.raitukashtam.mycommunity.response;

import com.raitukashtam.mycommunity.entity.ComplaintPriority;
import com.raitukashtam.mycommunity.entity.ComplaintStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintResponse {
    private Long id;
    private Long communityId;
    private Long raisedByMemberId;
    private String raisedByName;
    private String category;
    private String title;
    private String description;
    private ComplaintPriority priority;
    private ComplaintStatus status;
    private Long assignedToMemberId;
    private String assignedToName;
    private LocalDateTime createdAt;
}
