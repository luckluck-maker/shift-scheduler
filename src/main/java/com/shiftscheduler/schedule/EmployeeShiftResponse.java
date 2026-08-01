package com.shiftscheduler.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

public record EmployeeShiftResponse(
        Long shiftId,
        LocalDate shiftDate,
        String shiftTypeName,

        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime,

        boolean crossesMidnight,
        Long preferenceId,
        String preferenceType,
        String preferenceReason
) {
}