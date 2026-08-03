package com.shiftscheduler.schedule;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RequirementSpec(

        @NotNull
        Long jobPositionId,

        @Min(0)
        @Max(50)
        int requiredCount,

        // Optional in the request. Left out means essential, which is the
        // common case and keeps older requests working.
        Boolean essential
) {

        public boolean essentialOrDefault() {
                return essential == null || essential;
        }
}