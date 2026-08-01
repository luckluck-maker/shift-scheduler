package com.shiftscheduler.schedule;

import jakarta.validation.constraints.NotNull;

public record VersionedRequest(

        @NotNull
        Long version
) {
}