package com.shiftscheduler.schedule;

import com.shiftscheduler.auth.CurrentUserProvider;
import com.shiftscheduler.domain.Assignment;
import com.shiftscheduler.domain.JobPosition;
import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.domain.ScheduleStatus;
import com.shiftscheduler.domain.Shift;
import com.shiftscheduler.domain.ShiftPreference;
import com.shiftscheduler.domain.ShiftRequirement;
import com.shiftscheduler.domain.ShiftType;
import com.shiftscheduler.repository.AssignmentRepository;
import com.shiftscheduler.repository.JobPositionRepository;
import com.shiftscheduler.repository.ScheduleRepository;
import com.shiftscheduler.repository.ShiftPreferenceRepository;
import com.shiftscheduler.repository.ShiftRepository;
import com.shiftscheduler.repository.ShiftRequirementRepository;
import com.shiftscheduler.repository.ShiftTypeRepository;
import com.shiftscheduler.web.ConflictException;
import com.shiftscheduler.web.ResourceNotFoundException;
import com.shiftscheduler.web.ValidationException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.shiftscheduler.notification.ScheduleNotifier;
import org.springframework.jms.core.JmsTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    private static final int DAYS_IN_WEEK = 7;

    private final ScheduleRepository scheduleRepository;
    private final ShiftRepository shiftRepository;
    private final ShiftRequirementRepository requirementRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final JobPositionRepository jobPositionRepository;
    private final ShiftPreferenceRepository preferenceRepository;
    private final AssignmentRepository assignmentRepository;
    private final ScheduleGuard guard;
    private final CurrentUserProvider currentUser;
    private final JmsTemplate jmsTemplate;

    public ScheduleService(ScheduleRepository scheduleRepository,
                           ShiftRepository shiftRepository,
                           ShiftRequirementRepository requirementRepository,
                           ShiftTypeRepository shiftTypeRepository,
                           JobPositionRepository jobPositionRepository,
                           ShiftPreferenceRepository preferenceRepository,
                           AssignmentRepository assignmentRepository,
                           ScheduleGuard guard,
                           CurrentUserProvider currentUser, JmsTemplate jmsTemplate) {
        this.scheduleRepository = scheduleRepository;
        this.shiftRepository = shiftRepository;
        this.requirementRepository = requirementRepository;
        this.shiftTypeRepository = shiftTypeRepository;
        this.jobPositionRepository = jobPositionRepository;
        this.preferenceRepository = preferenceRepository;
        this.assignmentRepository = assignmentRepository;
        this.guard = guard;
        this.currentUser = currentUser;
        this.jmsTemplate = jmsTemplate;
    }

    @Transactional(readOnly = true)
    public List<ScheduleSummaryResponse> findAll() {
        // The list itself is not sensitive: it only says which weeks exist,
        // which an employee already knows from submitting on them. What each
        // role may open is decided per endpoint.
        List<Schedule> schedules = scheduleRepository.findAllByOrderByWeekStartDesc();

        return schedules.stream()
                .map(schedule -> new ScheduleSummaryResponse(
                        schedule.getId(),
                        schedule.getWeekStart(),
                        weekEnd(schedule),
                        schedule.getStatus().name(),
                        schedule.getVersion(),
                        shiftRepository.findByScheduleIdOrderByShiftDateAscIdAsc(schedule.getId()).size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScheduleDetailResponse findById(Long id) {
        Schedule schedule = requireVisible(id);

        List<Shift> shifts = shiftRepository.findByScheduleIdOrderByShiftDateAscIdAsc(id);

        Map<Long, List<ShiftRequirement>> byShift =
                requirementRepository.findByShiftScheduleIdOrderByIdAsc(id).stream()
                        .collect(Collectors.groupingBy(requirement -> requirement.getShift().getId()));

        List<ShiftResponse> shiftResponses = shifts.stream()
                .map(shift -> toShiftResponse(shift, byShift.getOrDefault(shift.getId(), List.of())))
                .toList();

        return new ScheduleDetailResponse(
                schedule.getId(),
                schedule.getWeekStart(),
                weekEnd(schedule),
                schedule.getStatus().name(),
                schedule.getVersion(),
                shiftResponses);
    }

    @Transactional(readOnly = true)
    public MyWeekResponse myWeek(Long scheduleId) {
        Schedule schedule = guard.require(scheduleId);

        Map<Long, ShiftPreference> preferences = preferenceRepository
                .findByShiftScheduleIdAndEmployeeIdOrderByShiftShiftDateAscIdAsc(
                        scheduleId, currentUser.employeeId())
                .stream()
                .collect(Collectors.toMap(
                        preference -> preference.getShift().getId(), Function.identity()));

        List<EmployeeShiftResponse> shifts =
                shiftRepository.findByScheduleIdOrderByShiftDateAscIdAsc(scheduleId).stream()
                        .map(shift -> toEmployeeShift(shift, preferences.get(shift.getId())))
                        .toList();

        return new MyWeekResponse(
                schedule.getId(),
                schedule.getWeekStart(),
                weekEnd(schedule),
                schedule.getStatus().name(),
                schedule.getStatus() == ScheduleStatus.COLLECTING,
                shifts);
    }

    @Transactional(readOnly = true)
    public RosterResponse roster(Long scheduleId) {
        Schedule schedule = guard.require(scheduleId);

        boolean published = schedule.getStatus() == ScheduleStatus.PUBLISHED;
        boolean visible = published || currentUser.isManager();

        // Before publishing the screen still opens, it just has nothing in it.
        // A 404 would look like an error to the client; an empty week reads as
        // "not out yet" and lets the employee keep browsing.
        List<RosterShift> shifts = visible ? buildRoster(scheduleId) : List.of();

        return new RosterResponse(
                schedule.getId(),
                schedule.getWeekStart(),
                weekEnd(schedule),
                schedule.getStatus().name(),
                published,
                shifts);
    }

    private List<RosterShift> buildRoster(Long scheduleId) {
        Long me = currentUser.employeeId();

        Map<Long, List<Assignment>> byShift = assignmentRepository
                .findByShiftScheduleIdOrderByShiftShiftDateAscIdAsc(scheduleId).stream()
                .collect(Collectors.groupingBy(assignment -> assignment.getShift().getId()));

        return shiftRepository.findByScheduleIdOrderByShiftDateAscIdAsc(scheduleId).stream()
                .map(shift -> toRosterShift(shift, byShift.getOrDefault(shift.getId(), List.of()), me))
                .toList();
    }

    private RosterShift toRosterShift(Shift shift, List<Assignment> assignments, Long me) {
        ShiftType type = shift.getShiftType();

        List<RosterAssignment> people = assignments.stream()
                .map(assignment -> {
                    var employee = assignment.getEmployee();
                    return new RosterAssignment(
                            employee.getId(),
                            employee.getFullName(),
                            employee.getJobPosition().getName(),
                            employee.getId().equals(me));
                })
                .toList();

        boolean assignedToMe = people.stream().anyMatch(RosterAssignment::isMe);

        return new RosterShift(
                shift.getId(),
                shift.getShiftDate(),
                type.getName(),
                type.getStartTime(),
                type.getEndTime(),
                type.isCrossesMidnight(),
                assignedToMe,
                people);
    }

    @Transactional
    public ScheduleDetailResponse create(ScheduleCreateRequest request) {
        LocalDate weekStart = request.weekStart();

        if (weekStart.getDayOfWeek() != DayOfWeek.SUNDAY) {
            throw new ValidationException("weekStart must be a Sunday");
        }

        if (scheduleRepository.existsByWeekStart(weekStart)) {
            throw new ConflictException(
                    "A schedule for the week starting " + weekStart + " already exists");
        }

        Schedule schedule = new Schedule();
        schedule.setWeekStart(weekStart);
        schedule.setStatus(ScheduleStatus.COLLECTING);
        scheduleRepository.save(schedule);

        if (request.shifts() == null || request.shifts().isEmpty()) {
            copyFromPrevious(schedule);
        } else {
            buildFromTemplate(schedule, request.shifts());
        }

        return findById(schedule.getId());
    }

    @Transactional
    public ScheduleDetailResponse lock(Long id, VersionedRequest request) {
        Schedule schedule = guard.require(id);
        guard.requireStatus(schedule, ScheduleStatus.COLLECTING);
        guard.requireVersion(schedule, request.version());

        schedule.setStatus(ScheduleStatus.DRAFT);
        guard.markChanged(schedule);

        return findById(id);
    }

    @Transactional
    public ScheduleDetailResponse publish(Long id, VersionedRequest request) {
        Schedule schedule = guard.require(id);
        guard.requireStatus(schedule, ScheduleStatus.DRAFT);
        guard.requireVersion(schedule, request.version());

        schedule.setStatus(ScheduleStatus.PUBLISHED);
        guard.markChanged(schedule);

        jmsTemplate.convertAndSend(ScheduleNotifier.TOPIC, id);

        return findById(id);
    }

    @Transactional
    public ShiftResponse replaceRequirements(Long scheduleId,
                                             Long shiftId,
                                             ShiftRequirementsUpdateRequest request) {
        Schedule schedule = guard.require(scheduleId);
        guard.requireStatus(schedule, ScheduleStatus.COLLECTING, ScheduleStatus.DRAFT);
        guard.requireVersion(schedule, request.version());

        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift " + shiftId + " not found"));

        if (!shift.getSchedule().getId().equals(scheduleId)) {
            throw new ValidationException(
                    "Shift " + shiftId + " does not belong to schedule " + scheduleId);
        }

        Set<Long> seen = new HashSet<>();
        for (RequirementSpec spec : request.requirements()) {
            if (!seen.add(spec.jobPositionId())) {
                throw new ValidationException(
                        "Job position " + spec.jobPositionId() + " appears more than once");
            }
        }

        Map<Long, JobPosition> positions = loadPositions(
                List.of(new ShiftTemplate(shift.getShiftType().getId(), request.requirements())));

        requirementRepository.deleteByShiftId(shiftId);
        requirementRepository.flush();

        List<ShiftRequirement> replacements = request.requirements().stream()
                .filter(spec -> spec.requiredCount() > 0)
                .map(spec -> newRequirement(
                        shift, positions.get(spec.jobPositionId()),
                        spec.requiredCount(), spec.essentialOrDefault()))
                .toList();

        requirementRepository.saveAll(replacements);
        guard.markChanged(schedule);

        return toShiftResponse(shift, replacements);
    }

    private void buildFromTemplate(Schedule schedule, List<ShiftTemplate> templates) {
        Map<Long, ShiftType> shiftTypes = loadShiftTypes(templates);
        Map<Long, JobPosition> positions = loadPositions(templates);

        List<Shift> shifts = new ArrayList<>();

        for (int dayOffset = 0; dayOffset < DAYS_IN_WEEK; dayOffset++) {
            LocalDate date = schedule.getWeekStart().plusDays(dayOffset);

            for (ShiftTemplate template : templates) {
                Shift shift = new Shift();
                shift.setSchedule(schedule);
                shift.setShiftDate(date);
                shift.setShiftType(shiftTypes.get(template.shiftTypeId()));
                shifts.add(shift);
            }
        }

        shiftRepository.saveAll(shifts);

        List<ShiftRequirement> requirements = new ArrayList<>();
        int index = 0;

        for (int dayOffset = 0; dayOffset < DAYS_IN_WEEK; dayOffset++) {
            for (ShiftTemplate template : templates) {
                Shift shift = shifts.get(index++);

                for (RequirementSpec spec : template.requirements()) {
                    if (spec.requiredCount() == 0) {
                        continue;
                    }

                    requirements.add(newRequirement(
                            shift, positions.get(spec.jobPositionId()),
                            spec.requiredCount(), spec.essentialOrDefault()));
                }
            }
        }

        requirementRepository.saveAll(requirements);
    }

    private void copyFromPrevious(Schedule schedule) {
        Schedule source = scheduleRepository
                .findFirstByWeekStartLessThanOrderByWeekStartDesc(schedule.getWeekStart())
                .orElseThrow(() -> new ValidationException(
                        "No earlier schedule to copy from. Provide the shifts explicitly."));

        List<Shift> sourceShifts =
                shiftRepository.findByScheduleIdOrderByShiftDateAscIdAsc(source.getId());

        if (sourceShifts.isEmpty()) {
            throw new ValidationException(
                    "The previous schedule has no shifts. Provide the shifts explicitly.");
        }

        Map<Long, List<ShiftRequirement>> sourceRequirements =
                requirementRepository.findByShiftScheduleIdOrderByIdAsc(source.getId()).stream()
                        .collect(Collectors.groupingBy(requirement -> requirement.getShift().getId()));

        List<Shift> copies = new ArrayList<>();

        for (Shift sourceShift : sourceShifts) {
            long dayOffset = ChronoUnit.DAYS.between(source.getWeekStart(), sourceShift.getShiftDate());

            Shift copy = new Shift();
            copy.setSchedule(schedule);
            copy.setShiftDate(schedule.getWeekStart().plusDays(dayOffset));
            copy.setShiftType(sourceShift.getShiftType());
            copies.add(copy);
        }

        shiftRepository.saveAll(copies);

        List<ShiftRequirement> requirements = new ArrayList<>();

        for (int i = 0; i < sourceShifts.size(); i++) {
            Shift copy = copies.get(i);

            for (ShiftRequirement original : sourceRequirements
                    .getOrDefault(sourceShifts.get(i).getId(), List.of())) {
                requirements.add(newRequirement(
                        copy, original.getJobPosition(), original.getRequiredCount(),
                        original.isEssential()));
            }
        }

        requirementRepository.saveAll(requirements);
    }

    private ShiftRequirement newRequirement(Shift shift, JobPosition position,
                                            int count, boolean essential) {
        ShiftRequirement requirement = new ShiftRequirement();
        requirement.setShift(shift);
        requirement.setJobPosition(position);
        requirement.setRequiredCount(count);
        requirement.setEssential(essential);
        return requirement;
    }

    private Map<Long, ShiftType> loadShiftTypes(List<ShiftTemplate> templates) {
        Set<Long> ids = templates.stream().map(ShiftTemplate::shiftTypeId).collect(Collectors.toSet());

        if (ids.size() != templates.size()) {
            throw new ValidationException("The same shift type appears more than once");
        }

        Map<Long, ShiftType> found = shiftTypeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ShiftType::getId, Function.identity()));

        for (Long id : ids) {
            if (!found.containsKey(id)) {
                throw new ResourceNotFoundException("Shift type " + id + " not found");
            }
        }

        return found;
    }

    private Map<Long, JobPosition> loadPositions(List<ShiftTemplate> templates) {
        Set<Long> ids = templates.stream()
                .flatMap(template -> template.requirements().stream())
                .map(RequirementSpec::jobPositionId)
                .collect(Collectors.toSet());

        Map<Long, JobPosition> found = jobPositionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(JobPosition::getId, Function.identity()));

        for (Long id : ids) {
            if (!found.containsKey(id)) {
                throw new ResourceNotFoundException("Job position " + id + " not found");
            }
        }

        return found;
    }

    private Schedule requireVisible(Long id) {
        Schedule schedule = guard.require(id);

        if (!currentUser.isManager() && schedule.getStatus() != ScheduleStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Schedule " + id + " not found");
        }

        return schedule;
    }

    private LocalDate weekEnd(Schedule schedule) {
        return schedule.getWeekStart().plusDays(DAYS_IN_WEEK - 1L);
    }

    private EmployeeShiftResponse toEmployeeShift(Shift shift, ShiftPreference preference) {
        ShiftType type = shift.getShiftType();

        return new EmployeeShiftResponse(
                shift.getId(),
                shift.getShiftDate(),
                type.getName(),
                type.getStartTime(),
                type.getEndTime(),
                type.isCrossesMidnight(),
                preference == null ? null : preference.getId(),
                preference == null ? null : preference.getType().name(),
                preference == null ? null : preference.getReason());
    }

    private ShiftResponse toShiftResponse(Shift shift, List<ShiftRequirement> requirements) {
        ShiftType type = shift.getShiftType();

        List<RequirementResponse> requirementResponses = requirements.stream()
                .map(requirement -> new RequirementResponse(
                        requirement.getId(),
                        requirement.getJobPosition().getId(),
                        requirement.getJobPosition().getName(),
                        requirement.getRequiredCount(),
                        requirement.isEssential()))
                .toList();

        return new ShiftResponse(
                shift.getId(),
                shift.getShiftDate(),
                type.getId(),
                type.getName(),
                type.getStartTime(),
                type.getEndTime(),
                type.isCrossesMidnight(),
                requirementResponses);
    }
}