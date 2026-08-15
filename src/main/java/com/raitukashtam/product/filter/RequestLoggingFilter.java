package com.raitukashtam.product.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String method = request.getMethod();
        String authorization = request.getHeader("Authorization");
        
        logger.info("=== PRODUCT SERVICE REQUEST START ===");
        logger.info("Method: {}, Path: {}", method, path);
        logger.info("Authorization header: {}", authorization != null ? "Present" : "Missing");
        logger.info("Remote address: {}", request.getRemoteAddr());
        logger.info("All headers: {}", request.getHeaderNames());
        
        try {
            filterChain.doFilter(request, response);
            logger.info("=== PRODUCT SERVICE REQUEST END ===");
            logger.info("Method: {}, Path: {}, Status: {}", method, path, response.getStatus());
        } catch (Exception e) {
            logger.error("=== PRODUCT SERVICE REQUEST ERROR ===");
            logger.error("Method: {}, Path: {}, Error: {}", method, path, e.getMessage(), e);
            throw e;
        }
    }
}
