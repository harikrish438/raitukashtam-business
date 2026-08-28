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
    private Integer totalUnits;
    private String street;
    private String area;
    private String district;
    private String state;
    private String pincode;
    private String landmark;
    private LocalDateTime createdAt;
}
