package com.shiftscheduler.employee;

import com.shiftscheduler.domain.Role;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmployeeUpdateRequest(

        @NotBlank
        @Size(max = 100)
        String fullName,

        @NotNull
        Role role,

        @Min(1)
        @Max(168)
        int maxWeeklyHours,

        boolean active,

        @NotNull
        Long jobPositionId
) {
}