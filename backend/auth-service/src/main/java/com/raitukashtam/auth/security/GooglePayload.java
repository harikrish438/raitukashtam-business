package com.raitukashtam.auth.security;

/**
 * Decoupled from Google's own GoogleIdToken.Payload so GoogleController
 * doesn't depend on the Google client library's types directly -- makes
 * GoogleTokenVerifierService trivially fakeable in tests (return a canned
 * GooglePayload instead of a real Google-signed JWT).
 */
public record GooglePayload(String subject, String email, String name, boolean emailVerified) {
}
