package com.raitukashtam.mycommunity.response;

import com.raitukashtam.mycommunity.entity.DocumentVisibility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    private Long id;
    private Long communityId;
    private String title;
    private String description;
    private String category;
    private DocumentVisibility visibility;
    private String contentType;
    private long fileSizeBytes;
    private Long uploadedByMemberId;
    private String uploadedByName;
    private LocalDateTime createdAt;
}
