package com.shiftscheduler.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

// The cookie the token travels in.
// HttpOnly keeps it away from JavaScript, SameSite=Strict stops another
// site sending it.
@Component
public class AuthCookieFactory {

    public static final String COOKIE_NAME = "access_token";

    private final boolean secure;
    private final long expirationMinutes;

    public AuthCookieFactory(@Value("${app.jwt.cookie-secure}") boolean secure,
                             @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.secure = secure;
        this.expirationMinutes = expirationMinutes;
    }

    public ResponseCookie issue(String token) {
        return base(token)
                .maxAge(Duration.ofMinutes(expirationMinutes))
                .build();
    }

    // Logout: the same cookie with a zero lifetime, which tells the browser
    // to drop it.
    public ResponseCookie expire() {
        return base("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/");
    }
}