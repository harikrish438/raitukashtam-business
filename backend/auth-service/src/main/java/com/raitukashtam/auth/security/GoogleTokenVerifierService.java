package com.raitukashtam.auth.security;

import java.util.Optional;

/**
 * Extracted from GoogleController (which used to build a
 * GoogleIdTokenVerifier inline per-request) so it can be swapped for a
 * test double -- the real implementation calls out to Google's own
 * certificate-verification network endpoint, which a test can't hit
 * without a genuine Google-signed token.
 */
public interface GoogleTokenVerifierService {

    /**
     * Empty if the token itself fails signature/audience/expiry
     * verification (caller should treat as a bad request). Throws
     * GoogleVerificationException for an infrastructure-level failure
     * (e.g. couldn't reach Google's certs endpoint) -- caller should
     * treat that as a server error, distinct from an invalid token.
     */
    Optional<GooglePayload> verify(String idToken);

    class GoogleVerificationException extends RuntimeException {
        public GoogleVerificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
