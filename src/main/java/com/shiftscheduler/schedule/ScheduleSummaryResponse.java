package com.shiftscheduler.schedule;

import java.time.LocalDate;

// One week without its shifts. Used for the week list.
public record ScheduleSummaryResponse(
        Long id,
        LocalDate weekStart,
        LocalDate weekEnd,
        String status,
        long version
) {
}