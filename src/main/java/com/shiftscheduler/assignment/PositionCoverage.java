package com.shiftscheduler.assignment;

public record PositionCoverage(
        Long jobPositionId,
        String jobPositionName,
        int required,
        int assigned,
        int missing,
        boolean essential
) {
}