package com.shiftscheduler.assignment;

import jakarta.validation.constraints.NotNull;

public record AssignmentCreateRequest(

        @NotNull
        Long shiftId,

        @NotNull
        Long employeeId,

        boolean override
) {
}