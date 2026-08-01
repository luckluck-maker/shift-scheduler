package com.shiftscheduler.assignment;

import java.time.LocalDate;
import java.util.List;

public record AssignmentResponse(
        Long id,
        Long shiftId,
        LocalDate shiftDate,
        String shiftTypeName,
        Long employeeId,
        String employeeName,
        String jobPositionName,
        boolean override,
        List<RuleViolation> warnings,
        List<String> overridesApplied
) {
}