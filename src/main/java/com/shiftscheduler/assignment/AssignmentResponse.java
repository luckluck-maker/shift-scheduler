package com.shiftscheduler.assignment;

import java.time.LocalDate;
import java.util.List;

// A saved assignment, with anything the manager should still see.
public record AssignmentResponse(
        Long id,
        Long shiftId,
        LocalDate shiftDate,
        String shiftTypeName,
        Long employeeId,
        String employeeName,
        Long jobPositionId,
        String jobPositionName,
        boolean override,
        long scheduleVersion,
        List<RuleViolation> warnings,
        List<String> overridesApplied
) {
}