package com.raitukashtam.mycommunity.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommunityResponse {
    private Long id;
    private String name;
    private String description;
    private Double price;
    private Double userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String modifiedBy;
    private String createdBy;
}
