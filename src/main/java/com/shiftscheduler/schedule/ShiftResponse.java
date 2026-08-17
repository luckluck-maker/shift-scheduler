package com.shiftscheduler.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// A shift and its staffing requirements.
public record ShiftResponse(
        Long id,
        LocalDate shiftDate,
        Long shiftTypeId,
        String shiftTypeName,

        // Sends 07:00 instead of 07:00:00.
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime,

        boolean crossesMidnight,
        List<RequirementResponse> requirements
) {
}