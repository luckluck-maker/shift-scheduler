package com.shiftscheduler.solver;

// hard = CANNOT, otherwise PREFERS_NOT.
// Only the constraints are here. No entry means the employee can work it.
public record ShiftDislike(Long employeeId, Long shiftId, boolean hard) {
}