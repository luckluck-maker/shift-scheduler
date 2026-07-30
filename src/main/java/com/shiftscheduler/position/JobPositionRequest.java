package com.shiftscheduler.position;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobPositionRequest(

        @NotBlank
        @Size(max = 60)
        String name
) {
}