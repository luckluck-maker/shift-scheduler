package com.shiftscheduler.schedule;

import java.time.LocalDate;

public record ScheduleSummaryResponse(
        Long id,
        LocalDate weekStart,
        LocalDate weekEnd,
        String status,
        int shiftCount
) {
}