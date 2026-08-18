package com.shiftscheduler.assignment;

public record AvailableEmployeeResponse(
        Long employeeId,
        String fullName,
        Long jobPositionId,
        String jobPositionName,

        // The rule that stops this assignment, or null when nothing does.
        // Will be turned into message by the frontend method
        String violatedRule

) {
}