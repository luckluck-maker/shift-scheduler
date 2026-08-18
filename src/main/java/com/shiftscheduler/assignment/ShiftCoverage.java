package com.shiftscheduler.assignment;

import java.time.LocalDate;
import java.util.List;

// One shift with the positions it needs and the people on it.
public record ShiftCoverage(
        Long shiftId,
        LocalDate shiftDate,
        String shiftTypeName,
        boolean fullyStaffed,
        List<PositionCoverage> positions,
        List<AssignmentResponse> assignments
) {
}