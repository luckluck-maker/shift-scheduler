package com.shiftscheduler.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// A shift and the people assigned to it.
public record RosterShift(
        Long shiftId,
        LocalDate shiftDate,
        String shiftTypeName,

        // Sends 07:00 instead of 07:00:00.
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime,

        boolean crossesMidnight,
        // True if the logged in employee is on this shift.
        boolean assignedToMe,
        List<RosterAssignment> assignments
) {
}