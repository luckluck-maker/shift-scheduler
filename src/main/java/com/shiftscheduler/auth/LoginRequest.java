package com.shiftscheduler.auth;

import jakarta.validation.constraints.NotBlank;

// Username and password.
public record LoginRequest(

        @NotBlank
        String username,

        @NotBlank
        String password
) {
}