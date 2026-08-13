package com.shiftscheduler.solver;

import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.domain.ScheduleStatus;
import com.shiftscheduler.schedule.ScheduleGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SolvePhase {

    private final ScheduleGuard guard;

    public SolvePhase(ScheduleGuard guard) {
        this.guard = guard;
    }

    // DRAFT -> SOLVING, called on the request thread before the solver starts.
    // requireStatus doubles as the lock: a second solve on the same week fails
    // here because it's no longer DRAFT.
    @Transactional
    public void begin(Long scheduleId) {
        Schedule schedule = guard.require(scheduleId);
        guard.requireStatus(schedule, ScheduleStatus.DRAFT);
        schedule.setStatus(ScheduleStatus.SOLVING);
        guard.markChanged(schedule);
    }

    // SOLVING -> DRAFT. Used for both success and failure, and it's idempotent:
    // if the week isn't SOLVING anymore it just leaves it alone.
    @Transactional
    public void end(Long scheduleId) {
        Schedule schedule = guard.require(scheduleId);
        if (schedule.getStatus() == ScheduleStatus.SOLVING) {
            schedule.setStatus(ScheduleStatus.DRAFT);
            guard.markChanged(schedule);
        }
    }
}