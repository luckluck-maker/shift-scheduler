package com.shiftscheduler.schedule;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record DeadlineRequest(

        @NotNull
        Long version,

        Instant submissionClosesAt
) {
}