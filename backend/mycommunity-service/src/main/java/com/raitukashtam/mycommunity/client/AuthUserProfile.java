package com.raitukashtam.mycommunity.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** Subset of auth-service's UserResponse (GET /users/me) this service actually needs. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthUserProfile {
    private String mobileNumber;
    private String firstName;
    private String lastName;
}
