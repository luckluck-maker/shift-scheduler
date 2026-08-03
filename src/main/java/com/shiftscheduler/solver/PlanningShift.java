package com.shiftscheduler.solver;


import ai.timefold.solver.core.api.domain.common.PlanningId;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

// A shift, from the solver's side.
// The database stores 23:00-07:00 as two clock times, which look backwards.
// The loader turned that into timestamps that both includes dates, so the night of 9 Aug
// ends 10 Aug 07:00 and the rest rule can subtract them.
public class PlanningShift {

    @PlanningId
    private Long id;

    private LocalDate shiftDate;
    private LocalDateTime start;
    private LocalDateTime end;
    private String shiftTypeName;

    public PlanningShift() {
    }

    public PlanningShift(Long id, LocalDate shiftDate, LocalDateTime start, LocalDateTime end,
                         String shiftTypeName) {
        this.id = id;
        this.shiftDate = shiftDate;
        this.start = start;
        this.end = end;
        this.shiftTypeName = shiftTypeName;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getShiftDate() {
        return shiftDate;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public String getShiftTypeName() {
        return shiftTypeName;
    }

    public long getDurationMinutes() {
        return Duration.between(start, end).toMinutes();
    }

    @Override
    public String toString() {
        return shiftTypeName + " " + shiftDate;
    }
}