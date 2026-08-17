package com.shiftscheduler.schedule;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// A staffing requirement sent from the client.
public record RequirementSpec(

        @NotNull
        Long jobPositionId,

        // Upper bound to catch a typo.
        @Min(0)
        @Max(50)
        int requiredCount,

        // Optional in the request. Left out means essential.
        Boolean essential
) {

        public boolean essentialOrDefault() {
                return essential == null || essential;
        }
}