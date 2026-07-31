package com.shiftscheduler.shifttype;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

public record ShiftTypeResponse(
        Long id,
        String name,

        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime,

        boolean crossesMidnight,
        long durationHours
) {
}