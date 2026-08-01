package com.shiftscheduler.assignment;

public record AvailableEmployeeResponse(
        Long employeeId,
        String fullName,
        Long jobPositionId,
        String jobPositionName,
        boolean available,
        String needsOverrideFor,
        boolean prefersNot
) {
}