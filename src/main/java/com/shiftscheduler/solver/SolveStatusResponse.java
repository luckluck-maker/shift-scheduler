package com.shiftscheduler.solver;

public record SolveStatusResponse(
        Long scheduleId,
        boolean solving,
        String status
) {
}