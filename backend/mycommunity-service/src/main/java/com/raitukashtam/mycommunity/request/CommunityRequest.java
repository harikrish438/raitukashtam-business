package com.raitukashtam.mycommunity.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CommunityRequest {
    @NotBlank(message = "Community name is required")
    private String name;

    @NotNull(message = "Number of units is required")
    @Positive(message = "Number of units must be a positive number")
    private Integer totalUnits;

    @NotBlank(message = "Street is required")
    private String street;

    @NotBlank(message = "Area is required")
    private String area;

    @NotBlank(message = "District is required")
    private String district;

    @NotBlank(message = "State is required")
    private String state;

    @NotBlank(message = "Pincode is required")
    private String pincode;

    private String landmark;

    @NotBlank(message = "Admin mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Admin mobile number must be a valid 10-digit Indian mobile number")
    private String adminMobile;
}
