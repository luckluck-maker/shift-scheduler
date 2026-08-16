package com.shiftscheduler.assignment;

import com.shiftscheduler.domain.Assignment;
import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.domain.JobPosition;
import com.shiftscheduler.domain.RosterChange;
import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.domain.ScheduleStatus;
import com.shiftscheduler.domain.Shift;
import com.shiftscheduler.domain.ShiftRequirement;
import com.shiftscheduler.repository.AssignmentRepository;
import com.shiftscheduler.repository.EmployeeLeaveRepository;
import com.shiftscheduler.repository.EmployeeRepository;
import com.shiftscheduler.repository.RosterChangeRepository;
import com.shiftscheduler.repository.ShiftPreferenceRepository;
import com.shiftscheduler.repository.ShiftRepository;
import com.shiftscheduler.repository.ShiftRequirementRepository;
import com.shiftscheduler.schedule.ScheduleGuard;
import com.shiftscheduler.web.ConflictException;
import com.shiftscheduler.web.ResourceNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftRequirementRepository requirementRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeLeaveRepository leaveRepository;
    private final ShiftPreferenceRepository preferenceRepository;
    private final RosterChangeRepository rosterChangeRepository;
    private final ScheduleRules rules;
    private final ScheduleGuard guard;

    public AssignmentService(AssignmentRepository assignmentRepository,
                             ShiftRepository shiftRepository,
                             ShiftRequirementRepository requirementRepository,
                             EmployeeRepository employeeRepository,
                             EmployeeLeaveRepository leaveRepository,
                             ShiftPreferenceRepository preferenceRepository,
                             RosterChangeRepository rosterChangeRepository,
                             ScheduleRules rules,
                             ScheduleGuard guard) {
        this.assignmentRepository = assignmentRepository;
        this.shiftRepository = shiftRepository;
        this.requirementRepository = requirementRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRepository = leaveRepository;
        this.preferenceRepository = preferenceRepository;
        this.rosterChangeRepository = rosterChangeRepository;
        this.rules = rules;
        this.guard = guard;
    }

    @Transactional
    public AssignmentResponse create(AssignmentCreateRequest request) {
        Shift shift = shiftRepository.findById(request.shiftId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shift " + request.shiftId() + " not found"));

        Schedule schedule = shift.getSchedule();
        guard.requireStatus(schedule, ScheduleStatus.DRAFT, ScheduleStatus.PUBLISHED);
        guard.requireVersion(schedule, request.scheduleVersion());

        Employee employee = employeeRepository.findByIdAndActiveTrue(request.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee " + request.employeeId() + " not found"));

        if (assignmentRepository.existsByShiftIdAndEmployeeId(shift.getId(), employee.getId())) {
            throw new ConflictException(
                    employee.getFullName() + " is already assigned to this shift");
        }

        List<RuleViolation> violations = new ArrayList<>(rules.check(employee, shift));

        boolean noSlot = !fitsRequirement(shift, employee);

        if (noSlot) {
            violations.add(RuleViolation.overridable(
                    ScheduleRules.RULE_NO_SLOT,
                    "This shift has no open slot for " + employee.getJobPosition().getName(),
                    "The assignment will not count towards the staffing requirement"));
        }

        List<RuleViolation> blocking = violations.stream()
                .filter(v -> RuleViolation.BLOCKING.equals(v.severity()))
                .toList();

        List<RuleViolation> overridable = violations.stream()
                .filter(v -> RuleViolation.OVERRIDABLE.equals(v.severity()))
                .toList();

        List<RuleViolation> warnings = violations.stream()
                .filter(v -> RuleViolation.WARNING.equals(v.severity()))
                .toList();

        if (!blocking.isEmpty()) {
            throw new AssignmentRejectedException(
                    "This assignment breaks rules that cannot be overridden",
                    blocking, overridable);
        }

        if (!overridable.isEmpty() && !request.override()) {
            throw new AssignmentRejectedException(
                    "This assignment needs confirmation before it can be saved",
                    List.of(), overridable);
        }

        List<String> applied = applyOverrides(employee, shift, overridable);

        Assignment assignment = new Assignment();
        assignment.setShift(shift);
        assignment.setEmployee(employee);
        assignment.setOverride(noSlot);

        Assignment saved = assignmentRepository.save(assignment);
        guard.markChanged(schedule);
        recordChange(schedule, employee, shift, true);

        return toResponse(saved, warnings, applied);
    }

    private List<String> applyOverrides(Employee employee, Shift shift,
                                        List<RuleViolation> overridable) {
        List<String> applied = new ArrayList<>();

        for (RuleViolation violation : overridable) {
            switch (violation.rule()) {
                case ScheduleRules.RULE_ON_LEAVE -> {
                    leaveRepository
                            .findByEmployeeIdAndLeaveDate(employee.getId(), shift.getShiftDate())
                            .ifPresent(leaveRepository::delete);
                    applied.add("Removed the leave on " + shift.getShiftDate());
                }
                case ScheduleRules.RULE_CANNOT_WORK -> {
                    preferenceRepository
                            .findByShiftScheduleIdAndEmployeeIdOrderByShiftShiftDateAscIdAsc(
                                    shift.getSchedule().getId(), employee.getId())
                            .stream()
                            .filter(preference -> preference.getShift().getId().equals(shift.getId()))
                            .findFirst()
                            .ifPresent(preferenceRepository::delete);
                    applied.add("Removed the stated constraint for this shift");
                }
                case ScheduleRules.RULE_NO_SLOT ->
                        applied.add("Assigned beyond the staffing requirement");
                case ScheduleRules.RULE_WEEKLY_HOURS ->
                        applied.add("Accepted the overtime beyond the contract");
                default -> { }
            }
        }

        return applied;
    }

    @Transactional
    public void clearShifts(Long scheduleId, List<Long> shiftIds, Long version) {
        Schedule schedule = guard.require(scheduleId);
        guard.requireStatus(schedule, ScheduleStatus.DRAFT);
        guard.requireVersion(schedule, version);

        // Filtering by schedule as well as by shift
        clearAssignments(schedule,
                assignmentRepository.findByShiftScheduleIdAndShiftIdIn(scheduleId, shiftIds));
    }

    @Transactional
    public void clearAll(Long scheduleId, Long version) {
        Schedule schedule = guard.require(scheduleId);
        guard.requireStatus(schedule, ScheduleStatus.DRAFT);
        guard.requireVersion(schedule, version);

        clearAssignments(schedule,
                assignmentRepository.
                        findByShiftScheduleIdOrderByShiftShiftDateAscIdAsc(scheduleId));
    }

    @Transactional
    public void delete(Long id, Long version) {
        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment " + id + " not found"));

        Schedule schedule = assignment.getShift().getSchedule();
        guard.requireStatus(schedule, ScheduleStatus.DRAFT, ScheduleStatus.PUBLISHED);
        guard.requireVersion(schedule, version);

        clearAssignments(schedule, List.of(assignment));
    }



    @Transactional(readOnly = true)
    public List<ShiftCoverage> coverage(Long scheduleId) {
        Schedule schedule = guard.require(scheduleId);

        List<Shift> shifts = shiftRepository.findByScheduleIdOrderByShiftDateAscIdAsc(schedule.getId());

        Map<Long, List<ShiftRequirement>> requirementsByShift =
                requirementRepository.findByShiftScheduleIdOrderByIdAsc(schedule.getId()).stream()
                        .collect(Collectors.groupingBy(r -> r.getShift().getId()));

        Map<Long, List<Assignment>> assignmentsByShift =
                assignmentRepository.findByShiftScheduleIdOrderByShiftShiftDateAscIdAsc(schedule.getId())
                        .stream()
                        .collect(Collectors.groupingBy(a -> a.getShift().getId()));

        return shifts.stream()
                .map(shift -> toCoverage(
                        shift,
                        requirementsByShift.getOrDefault(shift.getId(), List.of()),
                        assignmentsByShift.getOrDefault(shift.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AvailableEmployeeResponse> availableFor(Long shiftId, Long jobPositionId) {
        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift " + shiftId + " not found"));

        Set<Long> alreadyAssigned = assignmentRepository.findByShiftId(shiftId).stream()
                .map(assignment -> assignment.getEmployee().getId())
                .collect(Collectors.toSet());

        return employeeRepository.findByActiveTrue(Sort.by("fullName")).stream()
                .filter(employee -> !alreadyAssigned.contains(employee.getId()))
                .filter(employee -> !alreadyAssigned.contains(employee.getId()))
                .filter(employee -> jobPositionId == null
                        || employee.getJobPosition().getId().equals(jobPositionId))
                .map(employee -> toAvailable(employee, shift))
                .toList();
    }

    // Runs the same checks the assignment itself will, so the list already
    // knows who can't be picked and why. Doing it per employee means a few
    // queries each, with a large roster this would be worth batching.
    private AvailableEmployeeResponse toAvailable(Employee employee, Shift shift) {
        JobPosition position = employee.getJobPosition();

        return new AvailableEmployeeResponse(
                employee.getId(),
                employee.getFullName(),
                position.getId(),
                position.getName(),
                worstRule(rules.check(employee, shift)));
    }

    // Blocking first, then anything overridable, then a preference. Only the
    // worst one matters.
    private String worstRule(List<RuleViolation> violations) {
        return Stream.of(RuleViolation.BLOCKING,
                        RuleViolation.OVERRIDABLE,
                        RuleViolation.WARNING)
                .flatMap(severity -> violations.stream()
                        .filter(violation -> severity.equals(violation.severity())))
                .map(RuleViolation::rule)
                .findFirst()
                .orElse(null);
    }

    private boolean fitsRequirement(Shift shift, Employee employee) {
        Long positionId = employee.getJobPosition().getId();

        int required = requirementRepository.findByShiftIdOrderByIdAsc(shift.getId()).stream()
                .filter(requirement -> requirement.getJobPosition().getId().equals(positionId))
                .mapToInt(ShiftRequirement::getRequiredCount)
                .sum();

        if (required == 0) {
            return false;
        }

        long assigned = assignmentRepository.findByShiftId(shift.getId()).stream()
                .filter(assignment -> !assignment.isOverride())
                .filter(assignment -> assignment.getEmployee().getJobPosition().getId().equals(positionId))
                .count();

        return assigned < required;
    }

    private ShiftCoverage toCoverage(Shift shift,
                                     List<ShiftRequirement> requirements,
                                     List<Assignment> assignments) {
        List<PositionCoverage> positions = new ArrayList<>();
        boolean fullyStaffed = true;

        for (ShiftRequirement requirement : requirements) {
            Long positionId = requirement.getJobPosition().getId();

            int assigned = (int) assignments.stream()
                    .filter(assignment -> !assignment.isOverride())
                    .filter(a -> a.getEmployee().getJobPosition().getId().equals(positionId))
                    .count();

            int missing = Math.max(0, requirement.getRequiredCount() - assigned);

            if (missing > 0) {
                fullyStaffed = false;
            }

            positions.add(new PositionCoverage(
                    positionId,
                    requirement.getJobPosition().getName(),
                    requirement.getRequiredCount(),
                    assigned,
                    missing, requirement.isEssential()));
        }

        List<AssignmentResponse> assignmentResponses = assignments.stream()
                .map(assignment -> toResponse(assignment, List.of(), List.of()))
                .toList();

        return new ShiftCoverage(
                shift.getId(),
                shift.getShiftDate(),
                shift.getShiftType().getName(),
                fullyStaffed,
                positions,
                assignmentResponses);
    }

    private AssignmentResponse toResponse(Assignment assignment,
                                          List<RuleViolation> warnings,
                                          List<String> applied) {
        Shift shift = assignment.getShift();
        Employee employee = assignment.getEmployee();

        return new AssignmentResponse(
                assignment.getId(),
                shift.getId(),
                shift.getShiftDate(),
                shift.getShiftType().getName(),
                employee.getId(),
                employee.getFullName(),
                employee.getJobPosition().getId(),
                employee.getJobPosition().getName(),
                assignment.isOverride(),
                shift.getSchedule().getVersion(),
                warnings,
                applied);
    }

    private void clearAssignments(Schedule schedule, List<Assignment> assigns) {
        // Recorded before the delete, because afterwards there is no row left
        // saying who was on the shift.
        for (Assignment assignment : assigns) {
            recordChange(schedule, assignment.getEmployee(), assignment.getShift(), false);
        }

        assignmentRepository.deleteAll(assigns);
        guard.markChanged(schedule);
    }

    // Remembers that this person needs to be told, but only on a week that is
    // already out. On a draft nobody has seen the schedule yet, so there is
    // nothing to announce.
    //
    // A change in the opposite direction on the same shift cancels the one
    // waiting instead of adding to it: taking someone off and putting them
    // straight back leaves them where they were published, so there is nothing
    // to tell them. Once no rows are left the person drops off the list.
    private void recordChange(Schedule schedule, Employee employee, Shift shift, boolean added) {
        if (schedule.getStatus() != ScheduleStatus.PUBLISHED) {
            return;
        }

        Optional<RosterChange> waiting = rosterChangeRepository
                .findByScheduleIdAndEmployeeIdAndShiftId(
                        schedule.getId(), employee.getId(), shift.getId());

        if (waiting.isPresent()) {
            if (waiting.get().isAdded() != added) {
                rosterChangeRepository.delete(waiting.get());
            }

            return;
        }

        RosterChange change = new RosterChange();
        change.setSchedule(schedule);
        change.setEmployee(employee);
        change.setShift(shift);
        change.setAdded(added);

        rosterChangeRepository.save(change);
    }
}