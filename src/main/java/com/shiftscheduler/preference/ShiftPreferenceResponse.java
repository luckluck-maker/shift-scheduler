package com.shiftscheduler.preference;

import java.time.LocalDate;

public record ShiftPreferenceResponse(
        Long id,
        Long shiftId,
        LocalDate shiftDate,
        String shiftTypeName,
        Long employeeId,
        String employeeName,
        String type,
        String reason
) {
}
