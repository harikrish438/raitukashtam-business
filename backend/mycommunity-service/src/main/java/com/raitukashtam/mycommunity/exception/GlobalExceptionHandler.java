package com.raitukashtam.mycommunity.exception;

import com.raitukashtam.mycommunity.response.DuplicateCommunityErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * ResourceNotFoundException/ResourceAlreadyExistsException are left to
 * Spring's default @ResponseStatus handling (see those classes) -- this
 * only exists for DuplicateCommunityException, which needs a structured
 * body (existingCommunityId/Name) a plain @ResponseStatus can't carry.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateCommunityException.class)
    public ResponseEntity<DuplicateCommunityErrorResponse> handleDuplicateCommunity(DuplicateCommunityException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new DuplicateCommunityErrorResponse(
                ex.getMessage(), ex.getExistingCommunityId(), ex.getExistingCommunityName()));
    }
}
