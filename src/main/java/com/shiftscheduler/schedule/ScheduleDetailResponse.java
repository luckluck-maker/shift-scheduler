package com.shiftscheduler.schedule;

import java.time.LocalDate;
import java.util.List;

public record ScheduleDetailResponse(
        Long id,
        LocalDate weekStart,
        LocalDate weekEnd,
        String status,
        List<ShiftResponse> shifts
) {
}