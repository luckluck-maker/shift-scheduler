package com.shiftscheduler.auth;

public record LoginResult(
        String token,
        LoginResponse user
) {
}