package com.shiftscheduler.assignment;

public record RuleViolation(
        String rule,
        String severity,
        String message,
        String consequence
) {
    public static final String BLOCKING = "BLOCKING";
    public static final String OVERRIDABLE = "OVERRIDABLE";
    public static final String WARNING = "WARNING";

    public static RuleViolation blocking(String rule, String message) {
        return new RuleViolation(rule, BLOCKING, message, null);
    }

    public static RuleViolation overridable(String rule, String message, String consequence) {
        return new RuleViolation(rule, OVERRIDABLE, message, consequence);
    }

    public static RuleViolation warning(String rule, String message) {
        return new RuleViolation(rule, WARNING, message, null);
    }
}