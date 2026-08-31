package com.raitukashtam.mycommunity.response;

import com.raitukashtam.mycommunity.entity.AmenityBookingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AmenityBookingResponse {
    private Long id;
    private Long communityId;
    private Long amenityId;
    private String amenityName;
    private Long memberId;
    private String memberName;
    private LocalDate bookingDate;
    private String slot;
    private AmenityBookingStatus status;
    private LocalDateTime createdAt;
}
