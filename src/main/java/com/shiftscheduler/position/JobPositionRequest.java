package com.shiftscheduler.position;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobPositionRequest(

        // Long enough for any real job title, short enough that a name can't
        // stretch a cell in the weekly grid out of line with the others.
        @NotBlank
        @Size(max = 25)
        String name
) {
}