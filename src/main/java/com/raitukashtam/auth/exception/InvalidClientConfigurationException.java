package com.raitukashtam.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidClientConfigurationException extends RuntimeException {
    public InvalidClientConfigurationException(String message) {
        super(message);
    }
}
