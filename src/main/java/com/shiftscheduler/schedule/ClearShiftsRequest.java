package com.shiftscheduler.schedule;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ClearShiftsRequest(
        @NotNull Long version,
        @NotEmpty List<Long> shiftIds
) {}