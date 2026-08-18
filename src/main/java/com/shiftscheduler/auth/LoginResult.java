package com.shiftscheduler.auth;

// What login produces: the token for the cookie, and the user for the body.
public record LoginResult(
        String token,
        LoginResponse user
) {
}