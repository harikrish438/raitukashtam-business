package com.raitukashtam.mycommunity.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VendorResponse {
    private Long id;
    private Long communityId;
    private String name;
    private String serviceType;
    private String contactPerson;
    private String phoneNumber;
    private boolean active;
}
