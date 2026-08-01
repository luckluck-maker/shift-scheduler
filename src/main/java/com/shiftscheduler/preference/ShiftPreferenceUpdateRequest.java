package com.shiftscheduler.preference;

import com.shiftscheduler.domain.PreferenceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ShiftPreferenceUpdateRequest(

        @NotNull
        PreferenceType type,

        @Size(max = 255)
        String reason
) {
}
