package com.shiftscheduler.solver;

import com.fasterxml.jackson.annotation.JsonInclude;

// Whether the solver is still running on this week.
public record SolveStatusResponse(
        Long scheduleId,
        boolean solving,
        String status,

        // Holds a message only when the last run on this week failed.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String error
) {
}
