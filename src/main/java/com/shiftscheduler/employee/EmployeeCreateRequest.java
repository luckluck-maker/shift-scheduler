package com.shiftscheduler.employee;

import com.shiftscheduler.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmployeeCreateRequest(

        @NotBlank
        @Size(max = 100)
        String fullName,

        // The work email serves dual use: for the login & to receive the publish notifications
        // The idea is that the worker should have access to the shift scheduler
        // only as long as he works for the company
        @NotBlank
        @Email
        @Size(max = 120)
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