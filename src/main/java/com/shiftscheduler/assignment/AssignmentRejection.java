package com.shiftscheduler.assignment;

import java.time.Instant;
import java.util.List;

// The body sent back when an assignment is refused.
public record AssignmentRejection(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<RuleViolation> blocking,
        List<RuleViolation> overridable
) {
}