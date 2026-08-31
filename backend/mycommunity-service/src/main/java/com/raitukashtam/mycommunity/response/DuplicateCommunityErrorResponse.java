package com.raitukashtam.mycommunity.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateCommunityErrorResponse {
    private String message;
    private Long existingCommunityId;
    private String existingCommunityName;
}
