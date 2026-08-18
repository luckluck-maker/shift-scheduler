package com.shiftscheduler.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

// Who is making the current request, read from the token.
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

    // Spring Security puts the logged in user here at the start of each request,
    // so any code can ask who is calling without being passed it.
    private Jwt jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt token)) {
            // Never happens through the API - the filter chain rejects a bad
            // token first. This just gives a clear message if a service is ever
            // called without a request.
            throw new IllegalStateException("No authenticated user in the security context");
        }

        return token;
    }
}