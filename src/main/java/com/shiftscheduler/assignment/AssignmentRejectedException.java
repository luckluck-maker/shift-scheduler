package com.shiftscheduler.assignment;

import java.util.List;

// Thrown when an assignment is refused. Carries the rules so the screen can
// list them.
public class AssignmentRejectedException extends RuntimeException {

    private final transient List<RuleViolation> blocking;
    private final transient List<RuleViolation> overridable;

    public AssignmentRejectedException(String message,
                                       List<RuleViolation> blocking,
                                       List<RuleViolation> overridable) {
        super(message);
        this.blocking = blocking;
        this.overridable = overridable;
    }

    public List<RuleViolation> getBlocking() {
        return blocking;
    }

    public List<RuleViolation> getOverridable() {
        return overridable;
    }
}