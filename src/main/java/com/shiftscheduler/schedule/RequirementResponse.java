package com.shiftscheduler.schedule;

public record RequirementResponse(
        Long id,
        Long jobPositionId,
        String jobPositionName,
        int requiredCount,
        boolean essential
) {
}