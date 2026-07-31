package com.shiftscheduler.employee;

public record EmployeeResponse(
        Long id,
        String fullName,
        String username,
        String role,
        int maxWeeklyHours,
        boolean active,
        Long jobPositionId,
        String jobPositionName
) {
}