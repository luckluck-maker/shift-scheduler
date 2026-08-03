package com.shiftscheduler.solver;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.*;

import java.time.Duration;

// The rules the solver scores a whole schedule by
// I've chosen to implement 3 levels : high, medium and soft
// The solver uses tier-based scoring: a higher tier will ignore any amount of points from a lower tier.
//   hard   - will include the illegal assignments (labor laws)
//   medium - will include the understaffed assignments
//   soft   - will include the employees preferences and fairness
public class ShiftConstraints implements ConstraintProvider {

    private static final int MIN_REST_HOURS = 8;
    private static final int MAX_SHIFTS_PER_WEEK = 6;

    // Setting parameter to differentiate between a whole empty position and a single missing slot
    private static final int EMPTY_POSITION_WEIGHT = 9;

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                oneShiftPerDay(factory),
                restBetweenShifts(factory),
                maxShiftsPerWeek(factory),
                unfilledSlot(factory),
                essentialPositionWithNobody(factory)
        };
    }

    // An employee works at most one shift a day.
    Constraint oneShiftPerDay(ConstraintFactory factory) {
        return factory.forEachUniquePair(ShiftSlot.class,
                        Joiners.equal(ShiftSlot::getEmployee),
                        Joiners.equal(slot -> slot.getShift().getShiftDate()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("One shift per day");
    }

    // At least 8 hours between the end of one shift and the start of the next.
    // The shift times are full timestamps, so a night ending 07:00 the next
    // morning is just a subtraction.
    Constraint restBetweenShifts(ConstraintFactory factory) {
        return factory.forEachUniquePair(ShiftSlot.class,
                        Joiners.equal(ShiftSlot::getEmployee))
                .filter((first, second) -> restHoursBetween(first, second) < MIN_REST_HOURS)
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Rest between shifts");
    }

    // No more than six shifts in the week.
    Constraint maxShiftsPerWeek(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .groupBy(ShiftSlot::getEmployee, ConstraintCollectors.count())
                .filter((employee, count) -> count > MAX_SHIFTS_PER_WEEK)
                .penalize(HardMediumSoftScore.ONE_HARD,
                        (employee, count) -> count - MAX_SHIFTS_PER_WEEK)
                .asConstraint("Max shifts per week");
    }

    // A slot nobody ended up in. Not forbidden, but the solver should avoid it
    // unless there is genuinely nobody left who can work.
    Constraint unfilledSlot(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ShiftSlot.class)
                .filter(slot -> slot.getEmployee() == null)
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint("Unfilled slot");
    }

    // An essential position in a shift with nobody assigned to.
    // The logic is that some positions are essential and some are not, and this needs to be handled differently
    // from just missing slots. For example, call center can operate without supervisor, but cannot without CSRs
    // This also includes the cases where all positions are essentials, and it will further penalize the solver if
    // a position is left unassigned - 1/2 and 1/2 is better than 2/2 and 0/2
    Constraint essentialPositionWithNobody(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(ShiftSlot.class)
                .filter(ShiftSlot::isEssential)
                .groupBy(slot -> slot.getShift().getId(),
                        ShiftSlot::getJobPositionId,
                        ConstraintCollectors.conditionally(
                                slot -> slot.getEmployee() != null,
                                ConstraintCollectors.count()))
                .filter((shiftId, positionId, filled) -> filled == 0)
                .penalize(HardMediumSoftScore.ofMedium(EMPTY_POSITION_WEIGHT))
                .asConstraint("Essential position with nobody");
    }

    private static long restHoursBetween(ShiftSlot first, ShiftSlot second) {
        var a = first.getShift();
        var b = second.getShift();

        if (!a.getEnd().isAfter(b.getStart())) {
            return Duration.between(a.getEnd(), b.getStart()).toHours();
        }

        if (!b.getEnd().isAfter(a.getStart())) {
            return Duration.between(b.getEnd(), a.getStart()).toHours();
        }

        return 0;
    }
}