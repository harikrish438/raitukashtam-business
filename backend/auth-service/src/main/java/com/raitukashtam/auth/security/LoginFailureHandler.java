package com.raitukashtam.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Redirects back to GET /login with enough context (which error, which
 * username) for the page to decide whether to render the captcha widget.
 */
public class LoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        String username = request.getParameter("username");
        String errorParam = exception instanceof CaptchaRequiredAuthenticationException
                ? "captcha_required" : "bad_credentials";
        String redirectUrl = UriComponentsBuilder.fromPath("/login")
                .queryParam("error", errorParam)
                .queryParam("username", username == null ? "" : username)
                .build()
                .toUriString();
        response.sendRedirect(redirectUrl);
    }
}
