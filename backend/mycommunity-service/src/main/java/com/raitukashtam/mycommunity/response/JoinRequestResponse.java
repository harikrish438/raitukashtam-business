package com.raitukashtam.mycommunity.response;

import com.raitukashtam.mycommunity.entity.JoinRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinRequestResponse {
    private Long id;
    private Long communityId;
    private String requesterName;
    private String requesterMobileNumber;
    private JoinRequestStatus status;
    private LocalDateTime createdAt;
}
