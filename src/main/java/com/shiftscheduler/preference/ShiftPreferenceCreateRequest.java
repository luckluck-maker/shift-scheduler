package com.shiftscheduler.preference;

import com.shiftscheduler.domain.PreferenceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// A new constraint on one shift.
public record ShiftPreferenceCreateRequest(

        @NotNull
        Long shiftId,

        @NotNull
        PreferenceType type,

        @Size(max = 255)
        String reason,

        Long employeeId
) {
}
