package com.shiftscheduler.employee;

import com.shiftscheduler.domain.Role;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// The editable fields.
public record EmployeeUpdateRequest(

        @NotNull
        Long version,

        @NotBlank
        @Size(max = 100)
        String fullName,

        @NotNull
        Role role,

        @Min(1)
        @Max(168)
        int maxWeeklyHours,

        // Whether the employee is active is not edited here. Turning someone off
        // releases them from schedules, so it is its own request, the same way a
        // schedule is published rather than having its status typed in.
        @NotNull
        Long jobPositionId
) {
}