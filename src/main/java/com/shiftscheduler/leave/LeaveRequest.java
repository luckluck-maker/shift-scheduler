package com.shiftscheduler.leave;

import com.shiftscheduler.domain.LeaveType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record LeaveRequest(

        @NotNull
        Long employeeId,

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate,

        @NotNull
        LeaveType type
) {
}
