package com.raitukashtam.mycommunity.response;

import com.raitukashtam.mycommunity.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private Long communityId;
    private String communityName;
    private NotificationType type;
    private Long referenceId;
    private String title;
    private String body;
    private boolean read;
    private LocalDateTime createdAt;
}
