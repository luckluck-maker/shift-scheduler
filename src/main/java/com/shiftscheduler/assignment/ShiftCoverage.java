package com.shiftscheduler.assignment;

import java.time.LocalDate;
import java.util.List;

public record ShiftCoverage(
        Long shiftId,
        LocalDate shiftDate,
        String shiftTypeName,
        boolean fullyStaffed,
        List<PositionCoverage> positions,
        List<AssignmentResponse> assignments
) {
}