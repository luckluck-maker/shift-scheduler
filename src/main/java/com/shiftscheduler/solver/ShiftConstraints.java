package com.shiftscheduler.solver;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.*;
import com.shiftscheduler.domain.PreferenceType;
import com.shiftscheduler.domain.SchedulingRules;

import java.time.Duration;

// The rules the solver scores a whole schedule by
// I've chosen to implement 3 levels : high, medium and soft
// The solver uses tier-based scoring: a higher tier will ignore any amount of points from a lower tier.
//   hard   - will include the illegal assignments (labor laws)
//   medium - will include the understaffed assignments
//   soft   - will include the employees preferences and fairness
public class ShiftConstraints implements ConstraintProvider {

    // Setting parameter to differentiate between a whole empty position and a single missing slot
    private static final int EMPTY_POSITION_WEIGHT = 9;

    // Setting parameter for PREFER_NOT cost
    private static final int PREFERENCE_WEIGHT = 3;

    // Parameters for overtime and Undertime.
    // Different values since getting less work than promised is worse than getting more.
    // Both squared, so two shifts out cost four times one.
    private static final int OVERTIME_WEIGHT = 3;
    private static final int UNDERTIME_WEIGHT = 4;

    // Getting 0 is worse than being one short.
    // Weighted above the minimum rule so the solver
    // spreads what little there is rather than leaving someone out entirely.
    private static final int NO_SHIFTS_WEIGHT = 3;

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                oneShiftPerDay(factory),
                restBetweenShifts(factory),
                maxShiftsPerWeek(factory),
                wrongPosition(factory),
                unfilledSlot(factory),
                essentialPositionWithNobody(factory),
                employeeOnLeave(factory),
                employeeCannotWork(factory),
                employeePrefersNotTo(factory),
                overtimeBeyondContract(factory),
                undertimeBelowContract(factory),
                belowMinimumShifts(factory),
                employeeWithNoShifts(factory)

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
                .filter((first, second) -> restHoursBetween(first, second) < SchedulingRules.MIN_REST_HOURS)
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Rest between shifts");
    }

    // No more than six shifts in the week.
    Constraint maxShiftsPerWeek(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .groupBy(ShiftSlot::getEmployee, ConstraintCollectors.count())
                .filter((employee, count) -> count > SchedulingRules.MAX_SHIFTS_PER_WEEK)
                .penalize(HardMediumSoftScore.ONE_HARD,
                        (employee, count) -> count - SchedulingRules.MAX_SHIFTS_PER_WEEK)
                .asConstraint("Max shifts per week");
    }

    // The employee in a slot has to actually hold the position it asks for.
    // Nothing else stops the solver putting a cook where a waiter is needed.
    Constraint wrongPosition(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .filter(slot -> !slot.getEmployee().getJobPositionId().equals(slot.getJobPositionId()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Wrong position for the slot");
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
                        ConstraintCollectors.toSet(ShiftSlot::getEmployee))
                .filter((shiftId, positionId, employees) ->
                        employees.size() == 1 && employees.contains(null))
                .penalize(HardMediumSoftScore.ofMedium(EMPTY_POSITION_WEIGHT))
                .asConstraint("Essential position with nobody");
    }

    // Assigned on a shift while leave has been entered for the same day shift day
    Constraint employeeOnLeave(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .join(UnavailableDay.class,
                        Joiners.equal(slot -> slot.getEmployee().getId(),
                                UnavailableDay::employeeId),
                        Joiners.equal(slot -> slot.getShift().getShiftDate(),
                                UnavailableDay::date))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Employee on leave");
    }

    // A shift the employee marked as one they can't work.
    Constraint employeeCannotWork(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .join(ShiftDislike.class,
                        Joiners.equal(slot -> slot.getEmployee().getId(),
                                ShiftDislike::employeeId),
                        Joiners.equal(slot -> slot.getShift().getId(),
                                ShiftDislike::shiftId))
                .filter((slot, dislike) -> dislike.type() == PreferenceType.CANNOT)
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Employee cannot work");
    }

    // A shift the employee would rather not work. Worth avoiding, but the least severe violation.
    // Therefore, set as soft.
    Constraint employeePrefersNotTo(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .join(ShiftDislike.class,
                        Joiners.equal(slot -> slot.getEmployee().getId(),
                                ShiftDislike::employeeId),
                        Joiners.equal(slot -> slot.getShift().getId(),
                                ShiftDislike::shiftId))
                .filter((slot, dislike) -> dislike.type() == PreferenceType.PREFERS_NOT)
                .penalize(HardMediumSoftScore.ofSoft(PREFERENCE_WEIGHT))
                .asConstraint("Employee prefers not to");
    }

    // More shifts than the contract covers. Overtime costs money, but it isn't
    // against the rules, so it sits with the preferences and not above them.
    Constraint overtimeBeyondContract(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .groupBy(ShiftSlot::getEmployee, ConstraintCollectors.count())
                .filter((employee, shifts) -> shifts > employee.getAvailableShifts())
                .penalize(HardMediumSoftScore.ofSoft(OVERTIME_WEIGHT),
                        (employee, shifts) -> squared(shifts - employee.getAvailableShifts()))
                .asConstraint("Overtime beyond contract");
    }

    // Fewer shifts than the contract covers. Squaring it is what spreads the
    // gap around - one person 2 shifts short costs more than 2 people
    // 1 shift short each.
    Constraint undertimeBelowContract(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .groupBy(ShiftSlot::getEmployee, ConstraintCollectors.count())
                .filter((employee, shifts) -> shifts < employee.getAvailableShifts())
                .penalize(HardMediumSoftScore.ofSoft(UNDERTIME_WEIGHT),
                        (employee, shifts) -> squared(employee.getAvailableShifts() - shifts))
                .asConstraint("Undertime below contract");
    }

    // Minimum shift rule. Undertime alone doesn't protect part-time workers.
    // Someone contracted for 2 shifts who loses 1 has lost half their week,
    // while someone on five who loses one has barely noticed. Undertime scores both the same
    // Set as medium rule in order to win against undertime &
    // it shouldn't compete against the other medium rules as they deal with
    // scenarios where shifts are not covered while help prioritizing more fair assignment overall.
    Constraint belowMinimumShifts(ConstraintFactory factory) {
        return factory.forEach(ShiftSlot.class)
                .groupBy(ShiftSlot::getEmployee, ConstraintCollectors.count())
                .filter((employee, shifts) -> shifts < employee.getMinimumShifts())
                .penalize(HardMediumSoftScore.ONE_MEDIUM,
                        (employee, shifts) -> (int) (employee.getMinimumShifts() - shifts))
                .asConstraint("Below minimum shifts");
    }

    // edge case of employee not getting any shifts at all - will be missing by belowMinimumShifts
    // since groupBy will not make a group for him
    Constraint employeeWithNoShifts(ConstraintFactory factory) {
        return factory.forEach(PlanningEmployee.class)
                .filter(employee -> employee.getMinimumShifts() > 0)
                .ifNotExists(ShiftSlot.class, Joiners.equal(e -> e, ShiftSlot::getEmployee))
                .penalize(HardMediumSoftScore.ofMedium(NO_SHIFTS_WEIGHT))
                .asConstraint("Employee with no shifts");
    }

    private static int squared(long shifts) {
        return  (int) (shifts * shifts);
    }


    // forEachUniquePair gives no order, so need to check which shift comes first
    // before subtracting.
    private static long restHoursBetween(ShiftSlot a, ShiftSlot b) {
        return SchedulingRules.restHoursBetween(
                a.getShift().getStart(), a.getShift().getEnd(),
                b.getShift().getStart(), b.getShift().getEnd());
    }
}