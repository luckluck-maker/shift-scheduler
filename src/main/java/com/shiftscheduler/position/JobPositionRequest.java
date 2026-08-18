package com.shiftscheduler.position;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// A new or renamed job position.
public record JobPositionRequest(

        @NotBlank
        @Size(max = 40)
        String name
) {
}