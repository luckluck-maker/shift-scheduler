package com.shiftscheduler.schedule;

public record RosterAssignment(
        Long employeeId,
        String fullName,
        String jobPositionName,
        boolean isMe
) {
}