package com.shiftscheduler.schedule;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

// Only the date is needed. The week is filled with the active shift types,
// and the job requirements are copied from the week before if there is one.
public record ScheduleCreateRequest(

        @NotNull
        LocalDate weekStart,

        // Optional, and the manager closes submissions by hand when it is left out.
        // A date in the past is refused, same as when the deadline is changed later.
        @Future
        Instant submissionClosesAt

) {
}