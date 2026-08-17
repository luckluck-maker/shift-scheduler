package com.shiftscheduler.schedule;

// A staffing requirement sent to the client.
public record RequirementResponse(
        Long id,
        Long jobPositionId,
        String jobPositionName,
        int requiredCount,
        boolean essential
) {
}