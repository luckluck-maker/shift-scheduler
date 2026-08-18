package com.shiftscheduler.solver;

// Whether the solver is still running on this week.
public record SolveStatusResponse(
        Long scheduleId,
        boolean solving,
        String status
) {
}