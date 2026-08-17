package com.shiftscheduler.schedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// The full list of staffing requirements for one shift.
// Anything not listed is removed.
public record ShiftRequirementsUpdateRequest(

        @NotNull
        Long version,

        @NotNull
        @Valid
        List<RequirementSpec> requirements
) {
}