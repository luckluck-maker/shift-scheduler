package com.shiftscheduler.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The new password.
public record PasswordChangeRequest(

        @NotBlank
        @Size(min = 8, max = 72)
        String newPassword
) {
}