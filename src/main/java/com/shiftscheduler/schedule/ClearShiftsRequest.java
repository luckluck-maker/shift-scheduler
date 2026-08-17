package com.shiftscheduler.schedule;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// The shifts to clear, and the version.
public record ClearShiftsRequest(
        @NotNull Long version,
        // At least one shift must be selected.
        @NotEmpty List<Long> shiftIds
) {}