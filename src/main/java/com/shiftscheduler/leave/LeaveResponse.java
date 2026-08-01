package com.shiftscheduler.leave;

import java.time.LocalDate;
import java.util.List;

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