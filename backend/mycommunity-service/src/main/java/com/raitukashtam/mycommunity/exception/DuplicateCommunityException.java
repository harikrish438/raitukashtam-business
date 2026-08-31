package com.raitukashtam.mycommunity.exception;

import lombok.Getter;

/**
 * Thrown when CommunityService.createCommunity detects an existing
 * community matching (name, pincode). Not a plain
 * ResourceAlreadyExistsException because the caller needs the existing
 * community's id to request to join it instead -- see
 * GlobalExceptionHandler for the structured 409 body this becomes.
 */
@Getter
public class DuplicateCommunityException extends RuntimeException {
    private final Long existingCommunityId;
    private final String existingCommunityName;

    public DuplicateCommunityException(Long existingCommunityId, String existingCommunityName) {
        super("A community matching this name and pincode is already registered: '" + existingCommunityName
                + "' (id=" + existingCommunityId + "). Request to join it instead via POST "
                + "/api/v1/communities/" + existingCommunityId + "/join-requests.");
        this.existingCommunityId = existingCommunityId;
        this.existingCommunityName = existingCommunityName;
    }
}
