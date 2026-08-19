package com.shiftscheduler.assignment;

import com.shiftscheduler.domain.Assignment;
import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.domain.EmployeeLeave;
import com.shiftscheduler.domain.PreferenceType;
import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.domain.Shift;
import com.shiftscheduler.domain.ShiftPreference;
import com.shiftscheduler.domain.ShiftType;
import com.shiftscheduler.repository.AssignmentRepository;
import com.shiftscheduler.repository.EmployeeLeaveRepository;
import com.shiftscheduler.repository.ShiftPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Covers the rules a manual assignment is checked against. The solver holds the
// same rules in SolverRules, and both read their numbers from
// RuleConstants.
class ManualRulesTest {

    private static final LocalDate SUN = LocalDate.of(2026, 8, 9);
    private static final LocalDate MON = SUN.plusDays(1);
    private static final Long SCHEDULE = 1L;

    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final EmployeeLeaveRepository leaves = mock(EmployeeLeaveRepository.class);
    private final ShiftPreferenceRepository preferences = mock(ShiftPreferenceRepository.class);

    private final ManualRules rules = new ManualRules(assignments, leaves, preferences);

    private final Schedule schedule = schedule();
    private final Employee maya = employee(3L, "Maya", 40);

    // Clears everything out of the way, and each test puts back only what it
    // needs.
    @BeforeEach
    void nothingInTheWay() {
        when(leaves.findByEmployeeIdAndLeaveDate(anyLong(), any())).thenReturn(Optional.empty());
        when(preferences.findByShiftScheduleIdAndEmployeeIdOrderByShiftShiftDateAscIdAsc(
                anyLong(), anyLong())).thenReturn(List.of());
        when(assignments.findByShiftScheduleIdAndEmployeeId(anyLong(), anyLong()))
                .thenReturn(List.of());
        when(assignments.findPublishedBefore(any(), any())).thenReturn(List.of());
    }

    @Test
    void aClearAssignmentBreaksNothing() {
        assertThat(rules.check(maya, morning(1L, SUN))).isEmpty();
    }

    // ----- leave -----

    @Test
    void assigningSomebodyOnLeaveCanBeOverridden() {
        Shift shift = morning(1L, SUN);
        when(leaves.findByEmployeeIdAndLeaveDate(maya.getId(), SUN))
                .thenReturn(Optional.of(leave(maya, SUN)));

        RuleViolation violation = only(rules.check(maya, shift));

        assertThat(violation.rule()).isEqualTo(ManualRules.RULE_ON_LEAVE);
        assertThat(violation.severity()).isEqualTo(RuleViolation.OVERRIDABLE);
        assertThat(violation.consequence()).isNotBlank();
    }

    // ----- the constraints the employee submitted -----

    @Test
    void aShiftTheEmployeeMarkedCannotCanBeOverridden() {
        Shift shift = morning(1L, SUN);
        givenPreference(shift, PreferenceType.CANNOT);

        RuleViolation violation = only(rules.check(maya, shift));

        assertThat(violation.rule()).isEqualTo(ManualRules.RULE_CANNOT_WORK);
        assertThat(violation.severity()).isEqualTo(RuleViolation.OVERRIDABLE);
    }

    // Prefers-not carries no consequence and never stops the assignment, since it
    // is only shown to the manager.
    @Test
    void aShiftTheEmployeeWouldRatherNotWorkIsOnlyAWarning() {
        Shift shift = morning(1L, SUN);
        givenPreference(shift, PreferenceType.PREFERS_NOT);

        RuleViolation violation = only(rules.check(maya, shift));

        assertThat(violation.rule()).isEqualTo(ManualRules.RULE_PREFERS_NOT);
        assertThat(violation.severity()).isEqualTo(RuleViolation.WARNING);
        assertThat(violation.consequence()).isNull();
    }

    // The preference is read for the whole week and then narrowed to this shift,
    // so a constraint on another day has to be ignored.
    @Test
    void aConstraintOnAnotherShiftIsIgnored() {
        Shift other = morning(2L, MON);
        when(preferences.findByShiftScheduleIdAndEmployeeIdOrderByShiftShiftDateAscIdAsc(
                SCHEDULE, maya.getId()))
                .thenReturn(List.of(preference(maya, other, PreferenceType.CANNOT)));

        assertThat(rules.check(maya, morning(1L, SUN))).isEmpty();
    }

    // ----- one shift a day -----

    @Test
    void aSecondShiftOnTheSameDayIsRefused() {
        givenAssigned(evening(2L, SUN));

        assertThat(rulesFor(morning(1L, SUN)))
                .contains(ManualRules.RULE_ONE_PER_DAY);
    }

    @Test
    void aShiftOnAnotherDayIsFine() {
        givenAssigned(morning(2L, MON.plusDays(1)));

        assertThat(rules.check(maya, morning(1L, SUN))).isEmpty();
    }

    // ----- rest between shifts -----

    @Test
    void aMorningStraightAfterANightIsRefused() {
        givenAssigned(night(2L, SUN));

        List<RuleViolation> found = rules.check(maya, morning(1L, MON));

        assertThat(rulesOf(found)).contains(ManualRules.RULE_REST);
        assertThat(severityOf(found, ManualRules.RULE_REST))
                .isEqualTo(RuleViolation.BLOCKING);
    }

    // The night shift of the last published week ends on Sunday morning, so the
    // rule has to reach back across the week boundary.
    @Test
    void aNightAtTheEndOfLastWeekIsCountedToo() {
        when(assignments.findPublishedBefore(any(), any()))
                .thenReturn(List.of(assignment(maya, night(9L, SUN.minusDays(1)))));

        assertThat(rulesFor(morning(1L, SUN))).contains(ManualRules.RULE_REST);
    }

    // Somebody else's night shift last week says nothing about this employee.
    @Test
    void aNightWorkedByAnotherEmployeeIsIgnored() {
        Employee eitan = employee(6L, "Eitan", 40);
        when(assignments.findPublishedBefore(any(), any()))
                .thenReturn(List.of(assignment(eitan, night(9L, SUN.minusDays(1)))));

        assertThat(rules.check(maya, morning(1L, SUN))).isEmpty();
    }

    // ----- six shifts a week -----

    @Test
    void aSeventhShiftInTheWeekIsRefused() {
        givenAssigned(sixMorningsFrom(MON));

        assertThat(rulesFor(morning(1L, SUN)))
                .contains(ManualRules.RULE_WEEKLY_SHIFTS);
    }

    @Test
    void aSixthShiftIsAllowed() {
        givenAssigned(fiveMorningsFrom(MON));

        assertThat(rulesFor(morning(1L, SUN)))
                .doesNotContain(ManualRules.RULE_WEEKLY_SHIFTS);
    }

    // ----- contracted hours -----

    @Test
    void goingOverTheContractedHoursCanBeOverridden() {
        Employee noa = employee(7L, "Noa", 24);
        givenAssigned(noa, morning(2L, MON), morning(3L, MON.plusDays(1)),
                morning(4L, MON.plusDays(2)));

        List<RuleViolation> found = rules.check(noa, morning(1L, SUN));

        assertThat(rulesOf(found)).contains(ManualRules.RULE_WEEKLY_HOURS);
        assertThat(severityOf(found, ManualRules.RULE_WEEKLY_HOURS))
                .isEqualTo(RuleViolation.OVERRIDABLE);
    }

    @Test
    void workingExactlyTheContractedHoursIsFine() {
        Employee noa = employee(7L, "Noa", 24);
        givenAssigned(noa, morning(2L, MON), morning(3L, MON.plusDays(1)));

        assertThat(rulesFor(noa, morning(1L, SUN)))
                .doesNotContain(ManualRules.RULE_WEEKLY_HOURS);
    }

    // ----- more than one rule at once -----

    // check collects every rule that applies rather than stopping at the first,
    // so the screen can show all of them together.
    @Test
    void everyBrokenRuleIsReported() {
        Shift shift = morning(1L, SUN);
        when(leaves.findByEmployeeIdAndLeaveDate(maya.getId(), SUN))
                .thenReturn(Optional.of(leave(maya, SUN)));
        givenPreference(shift, PreferenceType.CANNOT);
        givenAssigned(evening(2L, SUN));

        assertThat(rulesOf(rules.check(maya, shift)))
                .contains(ManualRules.RULE_ON_LEAVE,
                        ManualRules.RULE_CANNOT_WORK,
                        ManualRules.RULE_ONE_PER_DAY);
    }

    // ----- setting up the mocks -----

    private void givenPreference(Shift shift, PreferenceType type) {
        when(preferences.findByShiftScheduleIdAndEmployeeIdOrderByShiftShiftDateAscIdAsc(
                SCHEDULE, maya.getId()))
                .thenReturn(List.of(preference(maya, shift, type)));
    }

    private void givenAssigned(Shift... shifts) {
        givenAssigned(maya, shifts);
    }

    private void givenAssigned(Employee employee, Shift... shifts) {
        when(assignments.findByShiftScheduleIdAndEmployeeId(SCHEDULE, employee.getId()))
                .thenReturn(java.util.Arrays.stream(shifts)
                        .map(shift -> assignment(employee, shift))
                        .toList());
    }

    // ----- reading the result -----

    private List<String> rulesFor(Shift shift) {
        return rulesFor(maya, shift);
    }

    private List<String> rulesFor(Employee employee, Shift shift) {
        return rulesOf(rules.check(employee, shift));
    }

    private static List<String> rulesOf(List<RuleViolation> violations) {
        return violations.stream().map(RuleViolation::rule).toList();
    }

    private static String severityOf(List<RuleViolation> violations, String rule) {
        return violations.stream()
                .filter(violation -> violation.rule().equals(rule))
                .map(RuleViolation::severity)
                .findFirst()
                .orElseThrow();
    }

    private static RuleViolation only(List<RuleViolation> violations) {
        assertThat(violations).hasSize(1);
        return violations.getFirst();
    }

    // ----- building the entities -----

    private Shift[] fiveMorningsFrom(LocalDate start) {
        return morningsFrom(start, 5);
    }

    private Shift[] sixMorningsFrom(LocalDate start) {
        return morningsFrom(start, 6);
    }

    // Spreads them a day apart, so only the shift count is in play.
    private Shift[] morningsFrom(LocalDate start, int count) {
        Shift[] shifts = new Shift[count];

        for (int i = 0; i < count; i++) {
            shifts[i] = morning(100L + i, start.plusDays(i));
        }

        return shifts;
    }

    private Shift morning(Long id, LocalDate date) {
        return shift(id, date, type(1L, "Morning", 7, 15, false));
    }

    private Shift evening(Long id, LocalDate date) {
        return shift(id, date, type(2L, "Evening", 15, 23, false));
    }

    private Shift night(Long id, LocalDate date) {
        return shift(id, date, type(3L, "Night", 23, 7, true));
    }

    private Shift shift(Long id, LocalDate date, ShiftType type) {
        Shift shift = new Shift();
        shift.setId(id);
        shift.setShiftDate(date);
        shift.setShiftType(type);
        shift.setSchedule(schedule);
        return shift;
    }

    private static ShiftType type(Long id, String name, int startHour, int endHour,
                                  boolean crossesMidnight) {
        ShiftType type = new ShiftType();
        type.setId(id);
        type.setName(name);
        type.setStartTime(LocalTime.of(startHour, 0));
        type.setEndTime(LocalTime.of(endHour, 0));
        type.setCrossesMidnight(crossesMidnight);
        return type;
    }

    private static Schedule schedule() {
        Schedule schedule = new Schedule();
        schedule.setId(SCHEDULE);
        schedule.setWeekStart(SUN);
        return schedule;
    }

    private static Employee employee(Long id, String name, int maxWeeklyHours) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setFullName(name);
        employee.setMaxWeeklyHours(maxWeeklyHours);
        return employee;
    }

    private static EmployeeLeave leave(Employee employee, LocalDate date) {
        EmployeeLeave leave = new EmployeeLeave();
        leave.setEmployee(employee);
        leave.setLeaveDate(date);
        return leave;
    }

    private static ShiftPreference preference(Employee employee, Shift shift, PreferenceType type) {
        ShiftPreference preference = new ShiftPreference();
        preference.setEmployee(employee);
        preference.setShift(shift);
        preference.setType(type);
        return preference;
    }

    private static Assignment assignment(Employee employee, Shift shift) {
        Assignment assignment = new Assignment();
        assignment.setEmployee(employee);
        assignment.setShift(shift);
        return assignment;
    }
}
