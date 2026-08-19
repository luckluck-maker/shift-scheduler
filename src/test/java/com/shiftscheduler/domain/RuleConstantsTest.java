package com.shiftscheduler.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

// Covers the arithmetic that SolverRules and ManualRules both read.
class RuleConstantsTest {

    private static final LocalDate SUN = LocalDate.of(2026, 8, 9);
    private static final LocalDate MON = SUN.plusDays(1);

    // ----- rest between two shifts -----

    @Test
    void countsTheHoursBetweenAMorningAndTheNextMorning() {
        assertThat(rest(at(SUN, 7), at(SUN, 15), at(MON, 7), at(MON, 15)))
                .isEqualTo(16);
    }

    // Checks the same pair both ways round, since the arguments don't say which
    // shift comes first.
    @Test
    void givesTheSameAnswerWhicheverShiftComesFirst() {
        long forwards = rest(at(SUN, 7), at(SUN, 15), at(MON, 7), at(MON, 15));
        long backwards = rest(at(MON, 7), at(MON, 15), at(SUN, 7), at(SUN, 15));

        assertThat(forwards).isEqualTo(backwards);
    }

    @Test
    void aNightShiftLeavesNothingBeforeTheMorningAfterIt() {
        assertThat(rest(at(SUN, 23), at(MON, 7), at(MON, 7), at(MON, 15)))
                .isZero();
    }

    @Test
    void shiftsThatOverlapCountAsNoRest() {
        assertThat(rest(at(SUN, 7), at(SUN, 15), at(SUN, 12), at(SUN, 20)))
                .isZero();
    }

    @Test
    void aShiftAgainstItselfCountsAsNoRest() {
        assertThat(rest(at(SUN, 7), at(SUN, 15), at(SUN, 7), at(SUN, 15)))
                .isZero();
    }

    @Test
    void aFullDayApartIsMoreThanTheMinimum() {
        assertThat(rest(at(SUN, 7), at(SUN, 15), at(MON, 15), at(MON, 23)))
                .isGreaterThanOrEqualTo(RuleConstants.MIN_REST_HOURS);
    }

    // ----- the minimum once leave is taken off -----

    @Test
    void somebodyWithNoLeaveOwesTheFullMinimum() {
        assertThat(RuleConstants.minimumShiftsWith(0))
                .isEqualTo(RuleConstants.MIN_SHIFTS_PER_WEEK);
    }

    // Lowers the minimum by one for every two days off, so a single day changes
    // nothing.
    @Test
    void oneDayOffDoesNotLowerTheMinimum() {
        assertThat(RuleConstants.minimumShiftsWith(1))
                .isEqualTo(RuleConstants.MIN_SHIFTS_PER_WEEK);
    }

    @Test
    void twoDaysOffLowerItByOne() {
        assertThat(RuleConstants.minimumShiftsWith(2))
                .isEqualTo(RuleConstants.MIN_SHIFTS_PER_WEEK - 1);
    }

    @Test
    void aWholeWeekOffOwesNothing() {
        assertThat(RuleConstants.minimumShiftsWith(7)).isZero();
    }

    // Floors the subtraction at zero, so more leave than the week holds still
    // leaves the minimum at zero.
    @Test
    void moreLeaveThanTheWeekHoldsStillOwesNothing() {
        assertThat(RuleConstants.minimumShiftsWith(40)).isZero();
    }

    private static long rest(LocalDateTime startA, LocalDateTime endA,
                             LocalDateTime startB, LocalDateTime endB) {
        return RuleConstants.restHoursBetween(startA, endA, startB, endB);
    }

    private static LocalDateTime at(LocalDate date, int hour) {
        return LocalDateTime.of(date, LocalTime.of(hour, 0));
    }
}
