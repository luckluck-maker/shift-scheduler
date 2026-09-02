package com.shiftscheduler.preference;

import com.shiftscheduler.domain.PreferenceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// The type and reason to change to.
public record ShiftPreferenceUpdateRequest(

        // The version the screen was showing.
        @NotNull
        Long version,

        @NotNull
        PreferenceType type,

        @Size(max = 255)
        String reason
) {
}
