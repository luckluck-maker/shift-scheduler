package com.shiftscheduler.schedule;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

// Only the date. Every week gets every shift type there is; the job requirements
// are copied from the previous week where there is one.
public record ScheduleCreateRequest(

        @NotNull
        LocalDate weekStart,

        // Optional. Without it the manager closes submissions by hand.
        Instant submissionClosesAt

) {
}