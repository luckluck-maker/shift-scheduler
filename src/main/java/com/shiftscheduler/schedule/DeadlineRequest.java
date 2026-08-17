package com.shiftscheduler.schedule;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

// The new closing time. Null means the manager closes the window himself.
public record DeadlineRequest(

        @NotNull
        Long version,

        @Future
        Instant submissionClosesAt
) {
}