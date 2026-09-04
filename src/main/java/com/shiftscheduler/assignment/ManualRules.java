package com.shiftscheduler.assignment;

import com.shiftscheduler.domain.*;
import com.shiftscheduler.repository.AssignmentRepository;
import com.shiftscheduler.repository.EmployeeLeaveRepository;
import com.shiftscheduler.repository.ShiftPreferenceRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// The rules checked when a manager assigns someone by hand.
// Blocking means the assignment is refused. Overridable means the manager
// can go ahead, and the consequence says what will change. Warning is shown
// only.
@Service
public class ManualRules {

    public static final String RULE_ON_LEAVE = "EMPLOYEE_ON_LEAVE";
    public static final String RULE_CANNOT_WORK = "EMPLOYEE_CANNOT_WORK";
    public static final String RULE_NO_SLOT = "NO_MATCHING_REQUIREMENT";
    public static final String RULE_PREFERS_NOT = "EMPLOYEE_PREFERS_NOT";
    public static final String RULE_REST = "REST_PERIOD";
    public static final String RULE_ONE_PER_DAY = "ONE_SHIFT_PER_DAY";
    public static final String RULE_WEEKLY_HOURS = "MAX_WEEKLY_HOURS";
    public static final String RULE_WEEKLY_SHIFTS = "MAX_SHIFTS_PER_WEEK";

    private final AssignmentRepository assignmentRepository;
    private final EmployeeLeaveRepository leaveRepository;
    private final ShiftPreferenceRepository preferenceRepository;

    public ManualRules(AssignmentRepository assignmentRepository,
                       EmployeeLeaveRepository leaveRepository,
                       ShiftPreferenceRepository preferenceRepository) {
        this.assignmentRepository = assignmentRepository;
        this.leaveRepository = leaveRepository;
        this.preferenceRepository = preferenceRepository;
    }

    // Collects every rule this assignment breaks.
    public List<RuleViolation> check(Employee employee, Shift shift) {
        List<RuleViolation> violations = new ArrayList<>();

        checkLeave(employee, shift, violations);
        checkPreference(employee, shift, violations);
        checkOtherAssignments(employee, shift, violations);

        return violations;
    }

    public static LocalDateTime startsAt(Shift shift) {
        return LocalDateTime.of(shift.getShiftDate(), shift.getShiftType().getStartTime());
    }

    // A night shift ends the next day, so the date moves with it.
    public static LocalDateTime endsAt(Shift shift) {
        ShiftType type = shift.getShiftType();
        LocalDate endDate = type.isCrossesMidnight()
                ? shift.getShiftDate().plusDays(1)
                : shift.getShiftDate();

        return LocalDateTime.of(endDate, type.getEndTime());
    }

    public static long durationHours(Shift shift) {
        return Duration.between(startsAt(shift), endsAt(shift)).toHours();
    }

    private void checkLeave(Employee employee, Shift shift, List<RuleViolation> violations) {
        leaveRepository
                .findByEmployeeIdAndLeaveDate(employee.getId(), shift.getShiftDate())
                .ifPresent(leave -> violations.add(RuleViolation.overridable(
                        RULE_ON_LEAVE,
                        employee.getFullName() + " is on leave on " + shift.getShiftDate(),
                        "The leave for that day will be removed")));
    }

    private void checkPreference(Employee employee, Shift shift, List<RuleViolation> violations) {
        preferenceRepository
                .findByShiftScheduleIdAndEmployeeIdOrderByShiftShiftDateAscIdAsc(
                        shift.getSchedule().getId(), employee.getId())
                .stream()
                .filter(preference -> preference.getShift().getId().equals(shift.getId()))
                .findFirst()
                .ifPresent(preference -> {
                    if (preference.getType() == PreferenceType.CANNOT) {
                        violations.add(RuleViolation.overridable(
                                RULE_CANNOT_WORK,
                                employee.getFullName() + " marked this shift as unavailable",
                                "The stated constraint will be removed"));
                    } else {
                        violations.add(RuleViolation.warning(
                                RULE_PREFERS_NOT,
                                employee.getFullName() + " would rather not work this shift"));
                    }
                });
    }

    // Loads the employee's other shifts in this week once, then checks the
    // whole list against them.
    private void checkOtherAssignments(Employee employee, Shift shift,
                                       List<RuleViolation> violations) {
        List<Assignment> existing = assignmentRepository
                .findByShiftScheduleIdAndEmployeeId(shift.getSchedule().getId(), employee.getId())
                .stream()
                .filter(assignment -> !assignment.getShift().getId().equals(shift.getId()))
                .toList();

        // 1 shift a day
        boolean sameDay = existing.stream()
                .anyMatch(assignment ->
                        assignment.getShift().getShiftDate().equals(shift.getShiftDate()));
        if (sameDay) {
            violations.add(RuleViolation.blocking(
                    RULE_ONE_PER_DAY,
                    employee.getFullName() + " is already assigned on " + shift.getShiftDate()));
        }

        // 8h rule from this week's schedule
        LocalDateTime start = startsAt(shift);
        LocalDateTime end = endsAt(shift);

        for (Assignment assignment : existing) {
            Shift other = assignment.getShift();
            long gap = RuleConstants.restHoursBetween(start, end, startsAt(other), endsAt(other));

            if (gap < RuleConstants.MIN_REST_HOURS) {
                violations.add(RuleViolation.blocking(
                        RULE_REST,
                        "Only " + gap + "h rest around the shift on " + other.getShiftDate()));
                break;
            }
        }

        // 8h rule from previous week
        // The rest rule also has to hold across the week boundary, so the
        // last published shift before this one is checked too.
        LocalDate shiftDate = shift.getShiftDate();
        for (Assignment prior : assignmentRepository.findPublishedBefore(shiftDate.minusDays(1), shiftDate)) {
            if (!prior.getEmployee().getId().equals(employee.getId())) {
                continue;
            }
            Shift other = prior.getShift();

            // Skips this week's shifts since the loop above already checked
            // them, so a published week doesn't report the same gap twice.
            if (!other.getShiftDate().isBefore(shift.getSchedule().getWeekStart())) {
                continue;
            }

            long gap = RuleConstants.restHoursBetween(start, end, startsAt(other), endsAt(other));
            if (gap < RuleConstants.MIN_REST_HOURS) {
                violations.add(RuleViolation.blocking(RULE_REST, "Only " + gap + "h rest after last week's shift on " + other.getShiftDate()));
                break;
            }
        }

        // Maximum 6 shifts a week
        long shiftCount = existing.size() + 1L;
        if (shiftCount > RuleConstants.MAX_SHIFTS_PER_WEEK) {
            violations.add(RuleViolation.blocking(
                    RULE_WEEKLY_SHIFTS,
                    employee.getFullName() + " would work " + shiftCount + " shifts this week"));
        }

        // overtime
        long totalHours = existing.stream().mapToLong(a -> durationHours(a.getShift())).sum()
                + durationHours(shift);

        if (totalHours > employee.getMaxWeeklyHours()) {
            violations.add(RuleViolation.overridable(
                    RULE_WEEKLY_HOURS,
                    employee.getFullName() + " would work " + totalHours + "h, over the contracted "
                            + employee.getMaxWeeklyHours() + "h",
                    "The extra hours count as overtime beyond the contract"));
        }
    }
}