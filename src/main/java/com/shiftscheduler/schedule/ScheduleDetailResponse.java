package com.shiftscheduler.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// One week with its shifts and their staffing requirements.
// Used by the build screen.
public record ScheduleDetailResponse(
        Long id,
        LocalDate weekStart,
        LocalDate weekEnd,
        String status,
        long version,
        Instant submissionClosesAt,
        // Employees waiting to be told the published week changed. 0 on any
        // week that is not published yet.
        long pendingChanges,
        List<ShiftResponse> shifts
) {
}