package com.raitukashtam.auth.filter;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.raitukashtam.auth.jwt.JwtTokenUtil;
import com.raitukashtam.auth.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (isPublicEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if(authHeader == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Token not found");
            return;
        }
        if (authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                if (token.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("Token is empty");
                    return;
                }

                DecodedJWT decodedJWT = jwtTokenUtil.validateAccessToken(token);
                String username = decodedJWT.getSubject();
                String role = decodedJWT.getClaim("role").asString();
                String jti = decodedJWT.getId();
                String tenantCode = decodedJWT.getClaim("tenant_code").asString();

                if (tokenService.isAccessTokenRevoked(jti)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("Token has been revoked");
                    return;
                }

                var auth = new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                );
                // Add tenant code as a detail
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // Set tenant code in details
                Map<String, Object> details = new HashMap<>();
                details.put("tenant_code", tenantCode);
                details.put("remoteAddress", request.getRemoteAddr());
                auth.setDetails(details);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (TokenExpiredException e) {
                response.setStatus(401);
                response.getWriter().write("Token expired");
                return;
            } catch (JWTVerificationException e) {
                response.setStatus(401);
                response.getWriter().write("Invalid token");
                return;
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Invalid token: " + e.getMessage());
                return;
            }
        } else {
            response.setStatus(401);
            response.getWriter().write("Invalid token");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getServletPath();
            return
                path.startsWith("/actuator") ||
                path.startsWith("/users/register") ||
                        path.startsWith("/google/verify-token") ||
                        path.startsWith("/forgot-password") ||
                        path.startsWith("/reset-password") ||
                path.startsWith("/token") ||
                path.startsWith("/refresh");
    }
}
