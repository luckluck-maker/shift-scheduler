package com.shiftscheduler.auth;

// The logged in user, for the screen.
public record CurrentUser(
        Long employeeId,
        String username,
        String fullName,
        String role
) {
}