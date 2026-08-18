package com.shiftscheduler.solver;

import java.util.List;

// The answer to starting a solve. The week is SOLVING when this comes back.
public record SolveResponse(
        Long scheduleId,
        String score,
        int slotCount,
        int pinnedCount,
        int filledBySolver,
        int stillEmpty,
        List<String> brokenRules
) {
}