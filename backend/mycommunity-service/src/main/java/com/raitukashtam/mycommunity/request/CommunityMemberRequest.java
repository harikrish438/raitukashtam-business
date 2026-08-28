package com.raitukashtam.mycommunity.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CommunityMemberRequest {
    @NotBlank(message = "Owner name is required")
    private String name;

    @NotBlank(message = "Unit number is required")
    private String unitNumber;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Mobile number must be a valid 10-digit Indian mobile number")
    private String mobileNumber;
}
