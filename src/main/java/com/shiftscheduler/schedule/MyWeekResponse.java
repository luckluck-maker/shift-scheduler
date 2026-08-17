package com.shiftscheduler.schedule;

import java.time.LocalDate;
import java.util.List;

// One week with the constraints of a single employee.
public record MyWeekResponse(
        Long scheduleId,
        LocalDate weekStart,
        LocalDate weekEnd,
        String status,
        // True while the week is still collecting constraints.
        boolean submissionOpen,
        List<EmployeeShiftResponse> shifts
) {
}