package com.raitukashtam.mycommunity.response;

import com.raitukashtam.mycommunity.entity.StaffRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffResponse {
    private Long id;
    private Long communityId;
    private String name;
    private StaffRole role;
    private String phoneNumber;
    private boolean active;
}
