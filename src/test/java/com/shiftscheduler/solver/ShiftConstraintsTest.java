package com.shiftscheduler.solver;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import com.shiftscheduler.domain.SchedulingRules;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

class ShiftConstraintsTest {

    private static final LocalDate SUN = LocalDate.of(2026, 8, 9);
    private static final LocalDate MON = SUN.plusDays(1);

    private static final Long AGENT = 2L;
    private static final Long SUPERVISOR = 1L;

    // Full time here is five shifts a week. Tests that care about the contract
    // set their own.
    private static final int FULL_TIME = 5;

    private final ConstraintVerifier<ShiftConstraints, EmployeeSchedule> verifier =
            ConstraintVerifier.build(new ShiftConstraints(), EmployeeSchedule.class, ShiftSlot.class);

    private final PlanningEmployee maya = employee(3L, "Maya");
    private final PlanningEmployee eitan = employee(6L, "Eitan");

    // ----- one shift per day -----

    @Test
    void twoShiftsOnTheSameDayArePenalised() {
        verifier.verifyThat(ShiftConstraints::oneShiftPerDay)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya),
                        filled(2L, shift(2L, SUN, 15, 23), maya))
                .penalizesBy(1);
    }

    @Test
    void twoShiftsOnDifferentDaysAreFine() {
        verifier.verifyThat(ShiftConstraints::oneShiftPerDay)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya),
                        filled(2L, shift(2L, MON, 7, 15), maya))
                .penalizesBy(0);
    }

    @Test
    void differentEmployeesOnTheSameDayAreFine() {
        verifier.verifyThat(ShiftConstraints::oneShiftPerDay)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya),
                        filled(2L, shift(2L, SUN, 15, 23), eitan))
                .penalizesBy(0);
    }

    // ----- rest between shifts -----

    @Test
    void nightThenMorningLeavesNoRest() {
        // 9 Aug 23:00 -> 10 Aug 07:00, then 10 Aug 07:00 -> 15:00
        verifier.verifyThat(ShiftConstraints::restBetweenShifts)
                .given(filled(1L, nightShift(1L, SUN), eitan),
                        filled(2L, shift(2L, MON, 7, 15), eitan))
                .penalizesBy(1);
    }

    @Test
    void aFullDayApartIsEnoughRest() {
        verifier.verifyThat(ShiftConstraints::restBetweenShifts)
                .given(filled(1L, shift(1L, SUN, 7, 15), eitan),
                        filled(2L, shift(2L, MON, 7, 15), eitan))
                .penalizesBy(0);
    }

    // ----- max shifts per week -----

    @Test
    void aSeventhShiftIsPenalised() {
        verifier.verifyThat(ShiftConstraints::maxShiftsPerWeek)
                .given((Object[]) week(7))
                .penalizesBy(1);
    }

    @Test
    void sixShiftsAreFine() {
        verifier.verifyThat(ShiftConstraints::maxShiftsPerWeek)
                .given((Object[]) week(6))
                .penalizesBy(0);
    }

    @Test
    void anEighthShiftCostsMoreThanASeventh() {
        verifier.verifyThat(ShiftConstraints::maxShiftsPerWeek)
                .given((Object[]) week(8))
                .penalizesBy(2);
    }

    // ----- unfilled slot -----

    @Test
    void anEmptySlotIsPenalised() {
        verifier.verifyThat(ShiftConstraints::unfilledSlot)
                .given(empty(1L, shift(1L, SUN, 7, 15), AGENT, true))
                .penalizesBy(1);
    }

    @Test
    void anEmptyNonEssentialSlotStillCountsHere() {
        verifier.verifyThat(ShiftConstraints::unfilledSlot)
                .given(empty(1L, shift(1L, SUN, 7, 15), SUPERVISOR, false))
                .penalizesBy(1);
    }

    @Test
    void aFilledSlotIsNotPenalised() {
        verifier.verifyThat(ShiftConstraints::unfilledSlot)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya))
                .penalizesBy(0);
    }

    // ----- essential position with nobody -----

    @Test
    void anEssentialPositionWithNobodyIsPenalised() {
        verifier.verifyThat(ShiftConstraints::essentialPositionWithNobody)
                .given(empty(1L, shift(1L, SUN, 7, 15), AGENT, true))
                .penalizesBy(1);
    }

    @Test
    void aPositionShortOnePersonIsNotEmpty() {
        PlanningShift morning = shift(1L, SUN, 7, 15);

        verifier.verifyThat(ShiftConstraints::essentialPositionWithNobody)
                .given(filled(1L, morning, maya),
                        empty(2L, morning, AGENT, true))
                .penalizesBy(0);
    }

    @Test
    void aNonEssentialPositionWithNobodyIsIgnoredHere() {
        verifier.verifyThat(ShiftConstraints::essentialPositionWithNobody)
                .given(empty(1L, shift(1L, SUN, 7, 15), SUPERVISOR, false))
                .penalizesBy(0);
    }

    @Test
    void everyEmptyEssentialPositionInAShiftCounts() {
        PlanningShift morning = shift(1L, SUN, 7, 15);

        verifier.verifyThat(ShiftConstraints::essentialPositionWithNobody)
                .given(empty(1L, morning, AGENT, true),
                        empty(2L, morning, 3L, true))
                .penalizesBy(2);
    }

    // ----- overtime beyond contract -----

    @Test
    void oneShiftOverTheContractIsPenalisedOnce() {
        verifier.verifyThat(ShiftConstraints::overtimeBeyondContract)
                .given((Object[]) weekFor(contracted(3), 4))
                .penalizesBy(1);
    }

    @Test
    void twoShiftsOverCostFourTimesAsMuch() {
        verifier.verifyThat(ShiftConstraints::overtimeBeyondContract)
                .given((Object[]) weekFor(contracted(3), 5))
                .penalizesBy(4);
    }

    @Test
    void workingTheContractExactlyIsFine() {
        verifier.verifyThat(ShiftConstraints::overtimeBeyondContract)
                .given((Object[]) weekFor(contracted(3), 3))
                .penalizesBy(0);
    }

    // ----- undertime below contract -----

    @Test
    void oneShiftUnderTheContractIsPenalisedOnce() {
        verifier.verifyThat(ShiftConstraints::undertimeBelowContract)
                .given((Object[]) weekFor(contracted(5), 4))
                .penalizesBy(1);
    }

    @Test
    void twoShiftsUnderCostFourTimesAsMuch() {
        verifier.verifyThat(ShiftConstraints::undertimeBelowContract)
                .given((Object[]) weekFor(contracted(5), 3))
                .penalizesBy(4);
    }

    // ----- helpers -----

    private static PlanningEmployee employee(Long id, String name) {
        return new PlanningEmployee(id, name, AGENT, FULL_TIME, SchedulingRules.MIN_SHIFTS_PER_WEEK);
    }

    // Someone whose contract is worth a set number of shifts this week.
    private static PlanningEmployee contracted(int shifts) {
        return new PlanningEmployee(7L, "Noa", AGENT, shifts, SchedulingRules.MIN_SHIFTS_PER_WEEK);
    }

    private static PlanningShift shift(Long id, LocalDate date, int startHour, int endHour) {
        return new PlanningShift(id, date,
                LocalDateTime.of(date, LocalTime.of(startHour, 0)),
                LocalDateTime.of(date, LocalTime.of(endHour, 0)),
                "Shift");
    }

    // 23:00 on the given day through to 07:00 the next morning.
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

    // A run of consecutive days worked by the same person.
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

        verifier.verifyThat(ShiftConstraints::wrongPosition)
                .given(slot)
                .penalizesBy(1);
    }

    @Test
    void theRightPositionIsNotPenalised() {
        verifier.verifyThat(ShiftConstraints::wrongPosition)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya))
                .penalizesBy(0);
    }


    @Test
    void beingBelowTheMinimumIsPenalised() {
        // contracted for 5, minimum 2, given only 1
        PlanningEmployee maya = new PlanningEmployee(3L, "Maya", AGENT, 5, 2);

        ShiftSlot slot = filled(1L, shift(1L, SUN, 7, 15), maya);

        verifier.verifyThat(ShiftConstraints::belowMinimumShifts)
                .given(slot)
                .penalizesBy(1);
    }

    @Test
    void meetingTheMinimumIsFine() {
        PlanningEmployee maya = new PlanningEmployee(3L, "Maya", AGENT, 5, 2);

        verifier.verifyThat(ShiftConstraints::belowMinimumShifts)
                .given(filled(1L, shift(1L, SUN, 7, 15), maya),
                        filled(2L, shift(2L, MON, 7, 15), maya))
                .penalizesBy(0);
    }
}

