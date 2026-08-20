package com.raitukashtam.auth.security;

import com.raitukashtam.auth.service.LoginAttemptService;
import jakarta.servlet.ServletException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.io.IOException;

/**
 * Replaces UsernamePasswordAuthenticationFilter at POST /login -- reads
 * the extra recaptchaToken field the default filter has no slot for, and
 * records each attempt via LoginAttemptService the same way
 * AuthController.login() used to (it needs the HttpServletRequest, which
 * only the filter -- not the AuthenticationProvider -- has direct access to).
 */
public class CaptchaAwareAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    private final LoginAttemptService loginAttemptService;

    public CaptchaAwareAuthenticationFilter(AuthenticationManager authenticationManager,
                                             LoginAttemptService loginAttemptService) {
        super(new AntPathRequestMatcher("/login", "POST"));
        setAuthenticationManager(authenticationManager);
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) {
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String recaptchaToken = request.getParameter("recaptchaToken");

        CaptchaAwareAuthenticationToken authRequest =
                new CaptchaAwareAuthenticationToken(username, password, recaptchaToken);
        authRequest.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return this.getAuthenticationManager().authenticate(authRequest);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                             FilterChain chain, Authentication authResult)
            throws IOException, ServletException {
        String username = request.getParameter("username");
        loginAttemptService.loginSucceeded(username, request);
        super.successfulAuthentication(request, response, chain, authResult);
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response,
                                               AuthenticationException failed) throws IOException, ServletException {
        // A CaptchaRequiredAuthenticationException means "show the widget",
        // not a real failed attempt -- checkLoginAttempts() didn't check
        // credentials at all in that case, so it isn't logged as one.
        if (!(failed instanceof CaptchaRequiredAuthenticationException)) {
            String username = request.getParameter("username");
            loginAttemptService.loginFailed(username, failed.getMessage(), request);
        }
        super.unsuccessfulAuthentication(request, response, failed);
    }
}
