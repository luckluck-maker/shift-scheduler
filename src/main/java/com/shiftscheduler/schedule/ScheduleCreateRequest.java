package com.shiftscheduler.schedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record ScheduleCreateRequest(

        @NotNull
        LocalDate weekStart,

        @NotEmpty
        @Valid
        List<ShiftTemplate> shifts
) {
}