package com.shiftscheduler.assignment;

import jakarta.validation.constraints.NotNull;

// Who to put on which shift. override confirms the manager saw the warnings.
public record AssignmentCreateRequest(

        @NotNull
        Long shiftId,

        @NotNull
        Long employeeId,

        boolean override,

        @NotNull
        Long scheduleVersion
) {
}