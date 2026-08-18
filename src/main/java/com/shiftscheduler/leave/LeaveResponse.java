package com.shiftscheduler.leave;

import java.time.LocalDate;
import java.util.List;

// One range of days off.
public record LeaveResponse(
        Long employeeId,
        String employeeName,
        LocalDate startDate,
        LocalDate endDate,
        String type,
        int days,
        List<Long> dayIds
) {
}