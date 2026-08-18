package com.shiftscheduler.auth;

// The user's details after a successful login.
public record LoginResponse(
        Long employeeId,
        String username,
        String fullName,
        String role
) {
}