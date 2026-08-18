package com.shiftscheduler.assignment;

// One position inside a shift: how many are needed, assigned and missing.
public record PositionCoverage(
        Long jobPositionId,
        String jobPositionName,
        int required,
        int assigned,
        int missing,
        boolean essential
) {
}