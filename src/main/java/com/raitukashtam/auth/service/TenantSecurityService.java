package com.raitukashtam.auth.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class TenantSecurityService {

    /**
     * Checks if the current user has access to the specified tenant
     * @param requestedTenantCode The tenant code being accessed
     * @return true if user has access, false otherwise
     */
    public boolean hasAccessToTenant(String requestedTenantCode) {
        if (requestedTenantCode == null) {
            return false;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        // Get tenant code from authentication details
        String userTenantCode = null;
        if (auth.getDetails() instanceof Map) {
            Map<String, String> details = (Map<String, String>) auth.getDetails();
            userTenantCode = details.get("tenant_code");
        }

        // If no tenant code in details, try to get it from principal
        if (userTenantCode == null && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User) {
            // If you store tenant code in UserDetails, you can get it here
            // userTenantCode = ((CustomUserDetails) auth.getPrincipal()).getTenantCode();
        }

        return requestedTenantCode.equals(userTenantCode);
    }

    /**
     * Gets the current user's tenant code
     * @return The tenant code or null if not available
     */
    public String getCurrentTenantCode() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Map) {
            return ((Map<String, String>) auth.getDetails()).get("tenant_code");
        }
        return null;
    }
}
