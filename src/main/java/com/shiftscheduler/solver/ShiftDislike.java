package com.shiftscheduler.solver;
import com.shiftscheduler.domain.PreferenceType;

// One constraint an employee set on a shift. CANNOT is penalised as a hard
// rule, PREFERS_NOT as a soft one, and no entry means they can work it.
public record ShiftDislike(Long employeeId, Long shiftId, PreferenceType type) {
}