package com.shiftscheduler.domain;

import java.time.Duration;
import java.time.LocalDateTime;

// The limits the scheduler works to. Some are legal - rest between shifts and
// the weekly cap - and some are policy, like the minimum. Both the manual
// checks and the solver read them from here so any change will be applied to both.
public final class SchedulingRules {

    // Statutory: at least this long between the end of one shift and the start
    // of the next. Rules out a night followed by a morning.
    public static final int MIN_REST_HOURS = 8;

    // Statutory: six working days a week.
    public static final int MAX_SHIFTS_PER_WEEK = 6;

    // Policy: nobody should end a week with almost nothing.
    public static final int MIN_SHIFTS_PER_WEEK = 2;
    private static final int LEAVE_DAYS_PER_SHIFT = 2;

    // A day off costs at most one shift, and not every day off would have been
    // a working day.
    public static int minimumShiftsWith(int leaveDays) {
        return Math.max(0, MIN_SHIFTS_PER_WEEK - leaveDays / LEAVE_DAYS_PER_SHIFT);
    }

    // Hours between two shifts, whichever order they come in. Overlapping
    // shifts fall through to zero.
    public static long restHoursBetween(LocalDateTime startA, LocalDateTime endA,
                                        LocalDateTime startB, LocalDateTime endB) {
        if (!endA.isAfter(startB)) {
            return Duration.between(endA, startB).toHours();
        }

        if (!endB.isAfter(startA)) {
            return Duration.between(endB, startA).toHours();
        }

        return 0;
    }
}