package com.shiftscheduler.solver;

// The answer to starting a solve. The week is SOLVING when this comes back.
public record SolveResponse(
        Long scheduleId,
        String status,
        int slotCount,
        int pinnedCount
) {
}
