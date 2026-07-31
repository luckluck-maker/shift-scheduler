package com.shiftscheduler.schedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ShiftRequirementsUpdateRequest(

        @NotNull
        @Valid
        List<RequirementSpec> requirements
) {
}