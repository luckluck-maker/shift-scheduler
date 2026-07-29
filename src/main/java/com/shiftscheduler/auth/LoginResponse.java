package com.shiftscheduler.auth;

public record LoginResponse(
        String token,
        Long employeeId,
        String username,
        String fullName,
        String role
) {
}