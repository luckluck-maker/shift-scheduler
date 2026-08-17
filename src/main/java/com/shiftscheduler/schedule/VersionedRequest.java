package com.shiftscheduler.schedule;

import jakarta.validation.constraints.NotNull;

// For actions that need nothing but the version.
public record VersionedRequest(

        @NotNull
        Long version
) {
}