package com.shiftscheduler.solver;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import com.shiftscheduler.domain.PreferenceType;
import com.shiftscheduler.domain.RuleConstants;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

class SolverRulesTest {

    private static final LocalDate SUN = LocalDate.of(2026, 8, 9);
    private static final LocalDate MON = SUN.plusDays(1);

    private static final Long AGENT = 2L;
    private static final Long SUPERVISOR = 1L;

    // Five shifts a week is full time here, and tests that care about the
    // contract set their own.
    private static final int FULL_TIME = 5;

    private final ConstraintVerifier<SolverRules, EmployeeSchedule> verifier =
            ConstraintVerifier.build(new SolverRules(), EmployeeSchedule.class, ShiftSlot.class);

    private final PlanningEmployee maya = employee(3L, "Maya");
    private final PlanningEmployee eitan = employee(6L, "Eitan");

    // ----- one shift per day -----

    @Test
    void twoShiftsOnTheSameDayArePenalised() {
        verifier.verifyThat(SolverRules::oneShiftPerDay)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya),
                        filled(2L, shift(2L, SUN, 15, 23), maya))
                .penalizesBy(1);
    }

    @Test
    void twoShiftsOnDifferentDaysAreFine() {
        verifier.verifyThat(SolverRules::oneShiftPerDay)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya),
                        filled(2L, shift(2L, MON, 7, 15), maya))
                .penalizesBy(0);
    }

    @Test
    void differentEmployeesOnTheSameDayAreFine() {
        verifier.verifyThat(SolverRules::oneShiftPerDay)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya),
                        filled(2L, shift(2L, SUN, 15, 23), eitan))
                .penalizesBy(0);
    }

    // ----- rest between shifts -----

    @Test
    void nightThenMorningLeavesNoRest() {
        // 9 Aug 23:00 -> 10 Aug 07:00, then 10 Aug 07:00 -> 15:00
        verifier.verifyThat(SolverRules::restBetweenShifts)
                .given(filled(1L, nightShift(1L, SUN), eitan),
                        filled(2L, shift(2L, MON, 7, 15), eitan))
                .penalizesBy(1);
    }

    @Test
    void aFullDayApartIsEnoughRest() {
        verifier.verifyThat(SolverRules::restBetweenShifts)
                .given(filled(1L, shift(1L, SUN, 7, 15), eitan),
                        filled(2L, shift(2L, MON, 7, 15), eitan))
                .penalizesBy(0);
    }

    // ----- max shifts per week -----

    @Test
    void aSeventhShiftIsPenalised() {
        verifier.verifyThat(SolverRules::maxShiftsPerWeek)
                .given((Object[]) week(7))
                .penalizesBy(1);
    }

    @Test
    void sixShiftsAreFine() {
        verifier.verifyThat(SolverRules::maxShiftsPerWeek)
                .given((Object[]) week(6))
                .penalizesBy(0);
    }

    @Test
    void anEighthShiftCostsMoreThanASeventh() {
        verifier.verifyThat(SolverRules::maxShiftsPerWeek)
                .given((Object[]) week(8))
                .penalizesBy(2);
    }

    // ----- unfilled slot -----

    @Test
    void anEmptySlotIsPenalised() {
        verifier.verifyThat(SolverRules::unfilledSlot)
                .given(empty(1L, shift(1L, SUN, 7, 15), AGENT, true))
                .penalizesBy(1);
    }

    @Test
    void anEmptyNonEssentialSlotStillCountsHere() {
        verifier.verifyThat(SolverRules::unfilledSlot)
                .given(empty(1L, shift(1L, SUN, 7, 15), SUPERVISOR, false))
                .penalizesBy(1);
    }

    @Test
    void aFilledSlotIsNotPenalised() {
        verifier.verifyThat(SolverRules::unfilledSlot)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya))
                .penalizesBy(0);
    }

    // ----- essential position with nobody -----

    @Test
    void anEssentialPositionWithNobodyIsPenalised() {
        verifier.verifyThat(SolverRules::essentialPositionWithNobody)
                .given(empty(1L, shift(1L, SUN, 7, 15), AGENT, true))
                .penalizesBy(1);
    }

    @Test
    void aPositionShortOnePersonIsNotEmpty() {
        PlanningShift morning = shift(1L, SUN, 7, 15);

        verifier.verifyThat(SolverRules::essentialPositionWithNobody)
                .given(filled(1L, morning, maya),
                        empty(2L, morning, AGENT, true))
                .penalizesBy(0);
    }

    @Test
    void aNonEssentialPositionWithNobodyIsIgnoredHere() {
        verifier.verifyThat(SolverRules::essentialPositionWithNobody)
                .given(empty(1L, shift(1L, SUN, 7, 15), SUPERVISOR, false))
                .penalizesBy(0);
    }

    @Test
    void everyEmptyEssentialPositionInAShiftCounts() {
        PlanningShift morning = shift(1L, SUN, 7, 15);

        verifier.verifyThat(SolverRules::essentialPositionWithNobody)
                .given(empty(1L, morning, AGENT, true),
                        empty(2L, morning, 3L, true))
                .penalizesBy(2);
    }

    // ----- overtime beyond contract -----

    @Test
    void oneShiftOverTheContractIsPenalisedOnce() {
        verifier.verifyThat(SolverRules::overtimeBeyondContract)
                .given((Object[]) weekFor(contracted(3), 4))
                .penalizesBy(1);
    }

    @Test
    void twoShiftsOverCostFourTimesAsMuch() {
        verifier.verifyThat(SolverRules::overtimeBeyondContract)
                .given((Object[]) weekFor(contracted(3), 5))
                .penalizesBy(4);
    }

    @Test
    void workingTheContractExactlyIsFine() {
        verifier.verifyThat(SolverRules::overtimeBeyondContract)
                .given((Object[]) weekFor(contracted(3), 3))
                .penalizesBy(0);
    }

    // ----- undertime below contract -----

    @Test
    void oneShiftUnderTheContractIsPenalisedOnce() {
        verifier.verifyThat(SolverRules::undertimeBelowContract)
                .given((Object[]) weekFor(contracted(5), 4))
                .penalizesBy(1);
    }

    @Test
    void twoShiftsUnderCostFourTimesAsMuch() {
        verifier.verifyThat(SolverRules::undertimeBelowContract)
                .given((Object[]) weekFor(contracted(5), 3))
                .penalizesBy(4);
    }

    // ----- helpers -----

    private static PlanningEmployee employee(Long id, String name) {
        return new PlanningEmployee(id, name, AGENT, FULL_TIME, RuleConstants.MIN_SHIFTS_PER_WEEK);
    }

    // Builds someone whose contract is worth a set number of shifts this week.
    private static PlanningEmployee contracted(int shifts) {
        return new PlanningEmployee(7L, "Noa", AGENT, shifts, RuleConstants.MIN_SHIFTS_PER_WEEK);
    }

    private static PlanningShift shift(Long id, LocalDate date, int startHour, int endHour) {
        return new PlanningShift(id, date,
                LocalDateTime.of(date, LocalTime.of(startHour, 0)),
                LocalDateTime.of(date, LocalTime.of(endHour, 0)),
                "Shift");
    }

    // Runs from 23:00 on the given day to 07:00 the next morning.
    private static PlanningShift nightShift(Long id, LocalDate date) {
        return new PlanningShift(id, date,
                LocalDateTime.of(date, LocalTime.of(23, 0)),
                LocalDateTime.of(date.plusDays(1), LocalTime.of(7, 0)),
                "Night");
    }

    private static ShiftSlot filled(Long id, PlanningShift shift, PlanningEmployee employee) {
        ShiftSlot slot = new ShiftSlot(id, shift, AGENT, "Support agent", true);
        slot.setEmployee(employee);
        return slot;
    }

    private static ShiftSlot empty(Long id, PlanningShift shift, Long positionId, boolean essential) {
        return new ShiftSlot(id, shift, positionId, "Position " + positionId, essential);
    }

    // Builds a run of consecutive days worked by the same person.
    private static ShiftSlot[] week(int shiftCount) {
        return weekFor(employee(3L, "Maya"), shiftCount);
    }

    private static ShiftSlot[] weekFor(PlanningEmployee employee, int shiftCount) {
        ShiftSlot[] slots = new ShiftSlot[shiftCount];

        for (int i = 0; i < shiftCount; i++) {
            ShiftSlot slot = filled(i + 1L, shift(i + 1L, SUN.plusDays(i), 7, 15), employee);
            slots[i] = slot;
        }

        return slots;
    }

    @Test
    void anEmployeeCannotFillAPositionTheyDoNotHold() {
        PlanningShift morning = shift(1L, SUN, 7, 15);

        ShiftSlot slot = new ShiftSlot(1L, morning, SUPERVISOR, "Shift supervisor", true);
        slot.setEmployee(maya);   // maya is an agent

        verifier.verifyThat(SolverRules::wrongPosition)
                .given(slot)
                .penalizesBy(1);
    }

    @Test
    void theRightPositionIsNotPenalised() {
        verifier.verifyThat(SolverRules::wrongPosition)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya))
                .penalizesBy(0);
    }


    @Test
    void beingBelowTheMinimumIsPenalised() {
        // contracted for 5, minimum 2, given only 1
        PlanningEmployee maya = new PlanningEmployee(3L, "Maya", AGENT, 5, 2);

        ShiftSlot slot = filled(1L, shift(1L, SUN, 7, 15), maya);

        verifier.verifyThat(SolverRules::belowMinimumShifts)
                .given(slot)
                .penalizesBy(1);
    }

    @Test
    void meetingTheMinimumIsFine() {
        PlanningEmployee maya = new PlanningEmployee(3L, "Maya", AGENT, 5, 2);

        verifier.verifyThat(SolverRules::belowMinimumShifts)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya),
                        filled(2L, shift(2L, MON, 7, 15), maya))
                .penalizesBy(0);
    }

    @Test
    void lastWeeksNightLeavesNoRestForSundayMorning() {
        // Eitan's Saturday-night shift last week ends Sunday 07:00.
        // A Sunday-morning shift this week starts 08:00 - one hour of rest.
        verifier.verifyThat(SolverRules::restAfterPreviousWeek)
                .given(filled(1L, shift(1L, SUN, 8, 16), eitan),
                        new PriorShiftEnd(eitan.getId(), LocalDateTime.of(SUN, LocalTime.of(7, 0))))
                .penalizesBy(1);
    }

    @Test
    void aFullDayAfterLastWeekIsEnoughRest() {
        // Ends Saturday 07:00, next shift Sunday 08:00 - 25 hours apart.
        verifier.verifyThat(SolverRules::restAfterPreviousWeek)
                .given(filled(1L, shift(1L, SUN, 8, 16), eitan),
                        new PriorShiftEnd(eitan.getId(),
                                LocalDateTime.of(SUN.minusDays(1), LocalTime.of(7, 0))))
                .penalizesBy(0);
    }

    // ----- employee on leave -----

    @Test
    void aShiftOnALeaveDayIsPenalised() {
        verifier.verifyThat(SolverRules::employeeOnLeave)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya),
                        new UnavailableDay(maya.getId(), SUN))
                .penalizesBy(1);
    }

    @Test
    void leaveOnAnotherDayIsFine() {
        verifier.verifyThat(SolverRules::employeeOnLeave)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya),
                        new UnavailableDay(maya.getId(), MON))
                .penalizesBy(0);
    }

    @Test
    void somebodyElsesLeaveIsFine() {
        verifier.verifyThat(SolverRules::employeeOnLeave)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya),
                        new UnavailableDay(eitan.getId(), SUN))
                .penalizesBy(0);
    }

    // ----- constraints the employee submitted -----

    @Test
    void aShiftMarkedCannotIsPenalised() {
        verifier.verifyThat(SolverRules::employeeCannotWork)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya),
                        new ShiftDislike(maya.getId(), 1L, PreferenceType.CANNOT))
                .penalizesBy(1);
    }

    // Prefers-not is a separate constraint, so this one leaves it alone.
    @Test
    void aShiftMarkedPrefersNotIsIgnoredByTheCannotRule() {
        verifier.verifyThat(SolverRules::employeeCannotWork)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya),
                        new ShiftDislike(maya.getId(), 1L, PreferenceType.PREFERS_NOT))
                .penalizesBy(0);
    }

    @Test
    void aDislikeOnAnotherShiftIsFine() {
        verifier.verifyThat(SolverRules::employeeCannotWork)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya),
                        new ShiftDislike(maya.getId(), 2L, PreferenceType.CANNOT))
                .penalizesBy(0);
    }

    @Test
    void aShiftMarkedPrefersNotIsPenalised() {
        verifier.verifyThat(SolverRules::employeePrefersNotTo)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya),
                        new ShiftDislike(maya.getId(), 1L, PreferenceType.PREFERS_NOT))
                .penalizesBy(1);
    }

    // Cannot-work is the harder rule, so this one leaves it alone.
    @Test
    void aShiftMarkedCannotIsIgnoredByThePrefersNotRule() {
        verifier.verifyThat(SolverRules::employeePrefersNotTo)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya),
                        new ShiftDislike(maya.getId(), 1L, PreferenceType.CANNOT))
                .penalizesBy(0);
    }

    // ----- an employee left out of the week -----

    @Test
    void anEmployeeWithNoShiftsAtAllIsPenalised() {
        verifier.verifyThat(SolverRules::employeeWithNoShifts)
                .given(maya)
                .penalizesBy(1);
    }

    @Test
    void anEmployeeWithOneShiftIsNotPenalisedHere() {
        verifier.verifyThat(SolverRules::employeeWithNoShifts)
                .given(maya, filled(1L, shift(1L, SUN, 7, 15), maya))
                .penalizesBy(0);
    }
}
