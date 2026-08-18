package com.shiftscheduler.shifttype;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

// A shift type with its hours.
public record ShiftTypeRequest(

        @NotBlank
        @Size(max = 40)
        String name,

        @NotNull
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @NotNull
        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime
) {
}