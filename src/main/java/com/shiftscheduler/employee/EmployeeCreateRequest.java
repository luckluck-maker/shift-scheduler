package com.shiftscheduler.employee;

import com.shiftscheduler.domain.Role;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmployeeCreateRequest(

        @NotBlank
        @Size(max = 100)
        String fullName,

        @NotBlank
        @Size(min = 3, max = 50)
        String username,

        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotNull
        Role role,

        @Min(1)
        @Max(168)
        int maxWeeklyHours,

        @NotNull
        Long jobPositionId
) {
}