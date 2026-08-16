package com.shiftscheduler.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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