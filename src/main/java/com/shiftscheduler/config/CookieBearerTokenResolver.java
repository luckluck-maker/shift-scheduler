package com.shiftscheduler.config;

import com.shiftscheduler.auth.AuthCookieFactory;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import java.util.Set;

// Pulls the token out of the access_token cookie instead of the Authorization
// header. Everything after this: checking the signature, the expiry.
public class CookieBearerTokenResolver implements BearerTokenResolver {

    // Logging in and out have to work while holding a token that is no longer
    // good, so the token is not read on those two.
    private static final Set<String> WITHOUT_A_TOKEN =
            Set.of("/api/auth/login", "/api/auth/logout");

    @Override
    public String resolve(HttpServletRequest request) {
        if (WITHOUT_A_TOKEN.contains(request.getRequestURI())) {
            return null;
        }

        Cookie[] cookies = request.getCookies();

        // No cookies at all on the request, which is normal for /login.
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (AuthCookieFactory.COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();

                // Logout leaves an empty cookie behind. Treat that as no token
                // rather than handing Spring a blank string to reject.
                return value.isBlank() ? null : value;
            }
        }

        return null;
    }
}