package com.shiftscheduler.schedule;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RequirementSpec(

        @NotNull
        Long jobPositionId,

        @Min(0)
        @Max(50)
        int requiredCount
) {
}