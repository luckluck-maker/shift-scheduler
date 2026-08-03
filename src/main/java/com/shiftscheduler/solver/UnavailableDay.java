package com.shiftscheduler.solver;

import java.time.LocalDate;

// One day of leave. Stored in the database 1 per day, so this will map 1:1.
public record UnavailableDay(Long employeeId, LocalDate date) {
}