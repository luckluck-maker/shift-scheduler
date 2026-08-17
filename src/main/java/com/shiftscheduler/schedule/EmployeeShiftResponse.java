package com.shiftscheduler.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

// A shift and the constraint one employee set on it.
public record EmployeeShiftResponse(
        Long shiftId,
        LocalDate shiftDate,
        String shiftTypeName,

        // Sends 07:00 instead of 07:00:00.
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