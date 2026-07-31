package com.shiftscheduler.auth;

public record LoginResponse(
        Long employeeId,
        String username,
        String fullName,
        String role
) {
}