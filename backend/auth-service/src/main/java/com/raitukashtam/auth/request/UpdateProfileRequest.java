package com.raitukashtam.auth.request;

import jakarta.validation.constraints.Email;
import lombok.Data;

/**
 * Self-service partial update -- a null field is left unchanged, a
 * present-but-blank one is rejected (see UserService.updateMyProfile).
 * Deliberately excludes mobileNumber: it's tied to OTP verification
 * (CredentialType.OTP_PHONE) and password-login uniqueness, so changing
 * it here without re-verifying possession would be a real account-takeover
 * risk -- out of scope for this endpoint.
 */
@Data
public class UpdateProfileRequest {
    private String firstName;
    private String lastName;

    @Email(message = "Email must be a valid email address")
    private String email;
}
