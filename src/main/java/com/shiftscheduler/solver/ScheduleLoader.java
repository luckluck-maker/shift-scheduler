package com.shiftscheduler.solver;

import com.shiftscheduler.domain.*;
import com.shiftscheduler.repository.AssignmentRepository;
import com.shiftscheduler.repository.EmployeeLeaveRepository;
import com.shiftscheduler.repository.EmployeeRepository;
import com.shiftscheduler.repository.ScheduleRepository;
import com.shiftscheduler.repository.ShiftPreferenceRepository;
import com.shiftscheduler.repository.ShiftRepository;
import com.shiftscheduler.repository.ShiftRequirementRepository;
import com.shiftscheduler.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.shiftscheduler.domain.SchedulingRules;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// Builds the object the solver works on based on the database.
// Reads only — nothing here decides anything or writes anything back.
//
// Everything runs inside one read-only transaction so the lazy relations are
// resolved here. The solver runs for 30 seconds afterwards with no transaction
// open, and by then it only ever sees plain values.
@Service
public class ScheduleLoader {

    // The last day of the previous week. A Saturday night shift ends on Sunday
    // morning, which is the overlap the rest rule would otherwise miss.
    private static final int LOOKBACK_DAYS = 1;
    // Only used if a schedule somehow has no shifts to measure.
    private static final int DEFAULT_SHIFT_MINUTES = 8 * 60;

    private static final int DAYS_IN_WEEK = 7;

    private final ScheduleRepository scheduleRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftRequirementRepository requirementRepository;
    private final AssignmentRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeLeaveRepository leaveRepository;
    private final ShiftPreferenceRepository preferenceRepository;

    public ScheduleLoader(ScheduleRepository scheduleRepository,
                          ShiftRepository shiftRepository,
                          ShiftRequirementRepository requirementRepository,
                          AssignmentRepository assignmentRepository,
                          EmployeeRepository employeeRepository,
                          EmployeeLeaveRepository leaveRepository,
                          ShiftPreferenceRepository preferenceRepository) {
        this.scheduleRepository = scheduleRepository;
        this.shiftRepository = shiftRepository;
        this.requirementRepository = requirementRepository;
        this.assignmentRepository = assignmentRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRepository = leaveRepository;
        this.preferenceRepository = preferenceRepository;
    }

    @Transactional(readOnly = true)
    public EmployeeSchedule load(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Schedule " + scheduleId + " not found"));

        LocalDate weekStart = schedule.getWeekStart();
        LocalDate weekEnd = weekStart.plusDays(DAYS_IN_WEEK - 1L);

        List<UnavailableDay> leave = loadLeave(weekStart, weekEnd);

        Map<Long, PlanningEmployee> employees =
                loadEmployees(leave, averageShiftMinutes(scheduleId));
        Map<Long, PlanningShift> shifts = loadShifts(scheduleId);

        // added to enable different solutions for each run
        List<PlanningEmployee> pool = new ArrayList<>(employees.values());
        Collections.shuffle(pool);

        return new EmployeeSchedule(
                scheduleId,
                pool,
                leave,
                loadDislikes(scheduleId),
                loadPriorShiftEnds(weekStart),
                buildSlots(scheduleId, shifts, employees));
    }

    // The contract is turned into a number of shifts here, where shift lengths
    // are known, and days off come straight off it. Someone away for three days
    // shouldn't be marked short for work they could never have done.
    private Map<Long, PlanningEmployee> loadEmployees(List<UnavailableDay> leave,
                                                      int shiftMinutes) {
        Map<Long, Long> leaveDays = leave.stream()
                .collect(Collectors.groupingBy(UnavailableDay::employeeId,
                        Collectors.counting()));

        return employeeRepository.findAll().stream()
                .filter(Employee::isActive)
                .map(employee -> {
                    int contracted = employee.getMaxWeeklyHours() * 60 / shiftMinutes;
                    int away = leaveDays.getOrDefault(employee.getId(), 0L).intValue();

                    return new PlanningEmployee(
                            employee.getId(),
                            employee.getFullName(),
                            employee.getJobPosition().getId(),
                            Math.max(0, contracted - away),
                            SchedulingRules.minimumShiftsWith(away));
                })
                .collect(Collectors.toMap(PlanningEmployee::getId, Function.identity()));
    }

    private Map<Long, PlanningShift> loadShifts(Long scheduleId) {
        return shiftRepository.findByScheduleIdOrderByShiftDateAscIdAsc(scheduleId).stream()
                .map(ScheduleLoader::toPlanningShift)
                .collect(Collectors.toMap(PlanningShift::getId, Function.identity()));
    }

    // Shifts are usually all the same length, but nothing stops a manager
    // defining a six-hour one. Averaging keeps the contract-in-shifts figure
    // roughly right either way.
    private int averageShiftMinutes(Long scheduleId) {
        List<Shift> shifts = shiftRepository.findByScheduleIdOrderByShiftDateAscIdAsc(scheduleId);

        if (shifts.isEmpty()) {
            return DEFAULT_SHIFT_MINUTES;
        }

        long total = shifts.stream()
                .mapToLong(shift -> toPlanningShift(shift).getDurationMinutes())
                .sum();

        return Math.max(1, (int) (total / shifts.size()));
    }

    // This is where crossesMidnight is resolved. From here on the solver deals
    // in timestamps and never has to think about it.
    private static PlanningShift toPlanningShift(Shift shift) {
        ShiftType type = shift.getShiftType();

        LocalDate endDate = type.isCrossesMidnight()
                ? shift.getShiftDate().plusDays(1)
                : shift.getShiftDate();

        return new PlanningShift(
                shift.getId(),
                shift.getShiftDate(),
                LocalDateTime.of(shift.getShiftDate(), type.getStartTime()),
                LocalDateTime.of(endDate, type.getEndTime()),
                type.getName());
    }

    // One slot per person a shift needs. Slots that already have someone are
    // pinned - the solver counts them in every rule but can't move them.
    // The ones left empty are what it fills in, so a partially staffed shift gets
    // completed.
    private List<ShiftSlot> buildSlots(Long scheduleId,
                                       Map<Long, PlanningShift> shifts,
                                       Map<Long, PlanningEmployee> employees) {

        Map<Long, List<Assignment>> assignmentsByShift =
                assignmentRepository.findByShiftScheduleIdOrderByShiftShiftDateAscIdAsc(scheduleId)
                        .stream()
                        .collect(Collectors.groupingBy(a -> a.getShift().getId()));

        Map<Long, List<ShiftRequirement>> requirementsByShift =
                requirementRepository.findByShiftScheduleIdOrderByIdAsc(scheduleId)
                        .stream()
                        .collect(Collectors.groupingBy(r -> r.getShift().getId()));

        List<ShiftSlot> slots = new ArrayList<>();
        long slotId = 1;

        for (Shift shift : shiftRepository.findByScheduleIdOrderByShiftDateAscIdAsc(scheduleId)) {
            PlanningShift planningShift = shifts.get(shift.getId());

            List<Assignment> assignments = assignmentsByShift.getOrDefault(shift.getId(), List.of());
            Set<Long> placed = new HashSet<>();

            for (ShiftRequirement requirement : requirementsByShift
                    .getOrDefault(shift.getId(), List.of())) {

                Long positionId = requirement.getJobPosition().getId();

                // Who is already on this shift in this position, manual extras
                // last so the ones counting towards the requirement fill up first.
                List<Assignment> existing = assignments.stream()
                        .filter(a -> a.getEmployee().getJobPosition().getId().equals(positionId))
                        .sorted((a, b) -> Boolean.compare(a.isOverride(), b.isOverride()))
                        .toList();

                long regular = existing.stream().filter(a -> !a.isOverride()).count();
                long overrides = existing.size() - regular;

                int slotCount = (int) (Math.max(requirement.getRequiredCount(), regular) + overrides);

                for (int i = 0; i < slotCount; i++) {
                    ShiftSlot slot = new ShiftSlot(
                            slotId++, planningShift, positionId,
                            requirement.getJobPosition().getName(),
                            requirement.isEssential());

                    if (i < existing.size()) {
                        Long employeeId = existing.get(i).getEmployee().getId();
                        slot.setEmployee(employees.get(employeeId));
                        slot.setPinned(true);
                        placed.add(employeeId);
                    }

                    slots.add(slot);
                }
            }

            // Someone assigned to a position this shift never asked for gets no
            // slot above, so they need one here. Leaving them out would hide from
            // the solver that they are working, and it could put them somewhere
            // that overlaps.
            for (Assignment assignment : assignments) {
                Long employeeId = assignment.getEmployee().getId();

                if (placed.contains(employeeId)) {
                    continue;
                }

                var position = assignment.getEmployee().getJobPosition();

                ShiftSlot slot = new ShiftSlot(
                        slotId++, planningShift, position.getId(), position.getName(), false);

                slot.setEmployee(employees.get(employeeId));
                slot.setPinned(true);
                slots.add(slot);

                placed.add(employeeId);
            }
        }

        return slots;
    }

    private List<UnavailableDay> loadLeave(LocalDate weekStart, LocalDate weekEnd) {
        return leaveRepository.findByLeaveDateBetween(weekStart, weekEnd).stream()
                .map(leave -> new UnavailableDay(
                        leave.getEmployee().getId(), leave.getLeaveDate()))
                .toList();
    }

    // Only Dislikes - CANNOT & PREFERS_NOT - are loaded. no entry means the employee is able to work
    private List<ShiftDislike> loadDislikes(Long scheduleId) {
        return preferenceRepository
                .findByShiftScheduleIdOrderByShiftShiftDateAscIdAsc(scheduleId).stream()
                .map(preference -> new ShiftDislike(
                        preference.getEmployee().getId(),
                        preference.getShift().getId(),
                        preference.getType()))
                .toList();
    }

    // The last shift each employee worked before this week started, from
    // published schedules only.
    // One shift a day is a hard rule, so there is at most one row per employee.
    private List<PriorShiftEnd> loadPriorShiftEnds(LocalDate weekStart) {
        return assignmentRepository
                .findPublishedBefore(weekStart.minusDays(LOOKBACK_DAYS), weekStart).stream()
                .map(assignment -> new PriorShiftEnd(
                        assignment.getEmployee().getId(),
                        toPlanningShift(assignment.getShift()).getEnd()))
                .toList();
    }
}