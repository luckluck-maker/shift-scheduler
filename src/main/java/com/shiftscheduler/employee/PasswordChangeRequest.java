package com.shiftscheduler.employee;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The new password.
public record PasswordChangeRequest(

        @NotBlank
        // Argon2 has no length limit of its own. NIST SP 800-63B asks for at
        // least 8 characters and for 64 to be accepted.
        @Size(min = 8, max = 64)
        String newPassword
) {
}