package com.shiftscheduler.auth;

public record CurrentUser(
        Long employeeId,
        String username,
        String fullName,
        String role
) {
}