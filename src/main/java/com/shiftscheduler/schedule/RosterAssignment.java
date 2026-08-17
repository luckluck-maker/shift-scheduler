package com.shiftscheduler.schedule;

// One assigned person inside a RosterShift.
public record RosterAssignment(
        Long employeeId,
        String fullName,
        String jobPositionName,
        // True if this is the logged in employee.
        boolean isMe
) {
}