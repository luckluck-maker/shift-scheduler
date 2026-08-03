package com.shiftscheduler.solver;

import java.time.LocalDateTime;

// Will handle the following edge case:
// A Saturday night into a Sunday morning breaks the rest rule,
// even though he's not presented in the current weekly schedule.
public record PriorShiftEnd(Long employeeId, LocalDateTime endsAt) {
}