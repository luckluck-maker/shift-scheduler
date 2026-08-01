package com.shiftscheduler.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record RosterShift(
        Long shiftId,
        LocalDate shiftDate,
        String shiftTypeName,

        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime,

        boolean crossesMidnight,
        boolean assignedToMe,
        List<RosterAssignment> assignments
) {
}