package com.shiftscheduler.solver;

import java.util.List;

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