package com.shiftscheduler.assignment;

import java.time.Instant;
import java.util.List;

public record AssignmentRejection(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<RuleViolation> blocking,
        List<RuleViolation> overridable
) {
}