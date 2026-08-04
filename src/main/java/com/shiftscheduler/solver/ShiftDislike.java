package com.shiftscheduler.solver;
import com.shiftscheduler.domain.PreferenceType;

// hard = CANNOT, otherwise PREFERS_NOT.
// Only the constraints are here. No entry means the employee can work it.
public record ShiftDislike(Long employeeId, Long shiftId, PreferenceType type) {
}