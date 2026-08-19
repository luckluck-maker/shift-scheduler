package com.shiftscheduler.domain;

import java.time.Duration;
import java.time.LocalDateTime;

// The numbers the scheduling rules use, not the rules themselves.
// The rules are in SolverRules for the solver and in ManualRules
// for manual assignment. Both read the numbers from here, so changing one
// applies to both.
public final class RuleConstants {

    // Statutory. Blocks a night shift followed by a morning.
    public static final int MIN_REST_HOURS = 8;

    // Statutory: six working days a week.
    public static final int MAX_SHIFTS_PER_WEEK = 6;

    // Policy: nobody should end a week with almost nothing.
    public static final int MIN_SHIFTS_PER_WEEK = 2;
    private static final int LEAVE_DAYS_PER_SHIFT = 2;

    // Every two days off lower the minimum by one shift.
    public static int minimumShiftsWith(int leaveDays) {
        return Math.max(0, MIN_SHIFTS_PER_WEEK - leaveDays / LEAVE_DAYS_PER_SHIFT);
    }

    // Hours between two shifts, in either order. Returns 0 if they overlap.
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