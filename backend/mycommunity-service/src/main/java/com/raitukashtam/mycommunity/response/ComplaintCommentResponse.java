package com.raitukashtam.mycommunity.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintCommentResponse {
    private Long id;
    private Long complaintId;
    private Long authorMemberId;
    private String authorName;
    private String comment;
    private LocalDateTime createdAt;
}
