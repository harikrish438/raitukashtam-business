package com.raitukashtam.mycommunity.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VendorRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @NotBlank(message = "Service type is required")
    @Size(max = 100, message = "Service type must be at most 100 characters")
    private String serviceType;

    private String contactPerson;

    private String phoneNumber;
}
