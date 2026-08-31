package com.raitukashtam.mycommunity.request;

import jakarta.validation.constraints.Email;
import lombok.Data;

/**
 * Self-service partial update -- a null field is left unchanged, an
 * empty/blank name is rejected (see CommunityService.updateMyProfile).
 * unitNumber is included because a join-request-approved member starts
 * with a placeholder "-" unit (unlike an admin-invited member, who has a
 * real unit set at invite time) and has no other way to correct it.
 */
@Data
public class MemberProfileUpdateRequest {
    private String name;

    @Email(message = "Email must be a valid email address")
    private String email;

    private String unitNumber;
}
