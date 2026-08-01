package com.shiftscheduler.schedule;

import java.time.LocalDate;
import java.util.List;

public record RosterResponse(
        Long scheduleId,
        LocalDate weekStart,
        LocalDate weekEnd,
        String status,
        boolean published,
        List<RosterShift> shifts
) {
}