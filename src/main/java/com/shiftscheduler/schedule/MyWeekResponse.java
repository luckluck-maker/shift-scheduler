package com.shiftscheduler.schedule;

import java.time.LocalDate;
import java.util.List;

public record MyWeekResponse(
        Long scheduleId,
        LocalDate weekStart,
        LocalDate weekEnd,
        String status,
        boolean submissionOpen,
        List<EmployeeShiftResponse> shifts
) {
}