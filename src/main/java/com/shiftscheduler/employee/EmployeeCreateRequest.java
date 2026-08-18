package com.shiftscheduler.employee;

import com.shiftscheduler.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// A new employee. Active from the start.
public record EmployeeCreateRequest(

        @NotBlank
        @Size(max = 100)
        String fullName,

        // The work email serves dual use: for the login & to receive the publish notifications
        // The idea is that the worker should have access to the shift scheduler
        // only as long as he works for the company
        @NotBlank
        @Email
        // RFC 5321 leaves 254 characters for the address itself.
        @Size(max = 254)
        String username,

        @NotBlank
        // Argon2 has no length limit of its own. NIST SP 800-63B asks for at
        // least 8 characters and for 64 to be accepted.
        @Size(min = 8, max = 64)
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