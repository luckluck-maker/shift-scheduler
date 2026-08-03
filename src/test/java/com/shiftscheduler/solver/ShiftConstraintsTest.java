package com.shiftscheduler.solver;

import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

class ShiftConstraintsTest {

    private static final LocalDate SUN = LocalDate.of(2026, 8, 9);
    private static final LocalDate MON = SUN.plusDays(1);

    private static final Long AGENT = 2L;
    private static final Long SUPERVISOR = 1L;

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
                .penalizesBy(1);          // ← היה 9
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
                .penalizesBy(2);          // ← היה 18
    }

    // ----- helpers -----

    private static PlanningEmployee employee(Long id, String name) {
        return new PlanningEmployee(id, name, AGENT, 40 * 60);
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

    private static ShiftSlot[] week(int shiftCount) {
        ShiftSlot[] slots = new ShiftSlot[shiftCount];

        for (int i = 0; i < shiftCount; i++) {
            slots[i] = filled(i + 1L, shift(i + 1L, SUN.plusDays(i), 7, 15), maya());
        }

        return slots;
    }

    private static PlanningEmployee maya() {
        return new PlanningEmployee(3L, "Maya", AGENT, 40 * 60);
    }
}