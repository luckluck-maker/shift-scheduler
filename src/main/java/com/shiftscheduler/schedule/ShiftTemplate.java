package com.shiftscheduler.schedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ShiftTemplate(

        @NotNull
        Long shiftTypeId,

        @NotEmpty
        @Valid
        List<RequirementSpec> requirements
) {
}