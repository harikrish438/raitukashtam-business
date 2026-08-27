package com.raitukashtam.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class TooManyFailedAttemptsException extends RuntimeException {
    public TooManyFailedAttemptsException(String message) {
        super(message);
    }
}
