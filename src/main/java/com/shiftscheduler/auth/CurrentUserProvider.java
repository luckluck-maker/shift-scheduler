package com.shiftscheduler.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    public Long employeeId() {
        Number id = jwt().getClaim("employeeId");
        return id == null ? null : id.longValue();
    }

    public String role() {
        return jwt().getClaim("role");
    }

    public boolean isManager() {
        return "MANAGER".equals(role());
    }

    private Jwt jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt token)) {
            throw new IllegalStateException("No authenticated user in the security context");
        }

        return token;
    }
}