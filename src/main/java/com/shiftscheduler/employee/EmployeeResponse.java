package com.shiftscheduler.employee;

// An employee for the list and the edit form.
public record EmployeeResponse(
        Long id,
        String fullName,
        String username,
        String role,
        int maxWeeklyHours,
        boolean active,
        Long jobPositionId,
        String jobPositionName,
        long version
) {
}