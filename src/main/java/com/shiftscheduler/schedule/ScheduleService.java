package com.shiftscheduler.schedule;

import com.shiftscheduler.auth.CurrentUserProvider;
import com.shiftscheduler.domain.JobPosition;
import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.domain.ScheduleStatus;
import com.shiftscheduler.domain.Shift;
import com.shiftscheduler.domain.ShiftRequirement;
import com.shiftscheduler.domain.ShiftType;
import com.shiftscheduler.repository.JobPositionRepository;
import com.shiftscheduler.repository.ScheduleRepository;
import com.shiftscheduler.repository.ShiftRepository;
import com.shiftscheduler.repository.ShiftRequirementRepository;
import com.shiftscheduler.repository.ShiftTypeRepository;
import com.shiftscheduler.web.ConflictException;
import com.shiftscheduler.web.ResourceNotFoundException;
import com.shiftscheduler.web.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final CurrentUserProvider currentUser;

    public ScheduleService(ScheduleRepository scheduleRepository,
                           ShiftRepository shiftRepository,
                           ShiftRequirementRepository requirementRepository,
                           ShiftTypeRepository shiftTypeRepository,
                           JobPositionRepository jobPositionRepository,
                           CurrentUserProvider currentUser) {
        this.scheduleRepository = scheduleRepository;
        this.shiftRepository = shiftRepository;
        this.requirementRepository = requirementRepository;
        this.shiftTypeRepository = shiftTypeRepository;
        this.jobPositionRepository = jobPositionRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<ScheduleSummaryResponse> findAll() {
        List<Schedule> schedules = currentUser.isManager()
                ? scheduleRepository.findAllByOrderByWeekStartDesc()
                : scheduleRepository.findByStatusOrderByWeekStartDesc(ScheduleStatus.PUBLISHED);

        return schedules.stream()
                .map(schedule -> new ScheduleSummaryResponse(
                        schedule.getId(),
                        schedule.getWeekStart(),
                        schedule.getWeekStart().plusDays(DAYS_IN_WEEK - 1),
                        schedule.getStatus().name(),
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
                schedule.getWeekStart().plusDays(DAYS_IN_WEEK - 1),
                schedule.getStatus().name(),
                shiftResponses);
    }

    @Transactional
    public ScheduleDetailResponse create(ScheduleCreateRequest request) {
        if (scheduleRepository.existsByWeekStart(request.weekStart())) {
            throw new ConflictException(
                    "A schedule for the week starting " + request.weekStart() + " already exists");
        }

        Map<Long, ShiftType> shiftTypes = loadShiftTypes(request.shifts());
        Map<Long, JobPosition> positions = loadPositions(request.shifts());

        Schedule schedule = new Schedule();
        schedule.setWeekStart(request.weekStart());
        schedule.setStatus(ScheduleStatus.DRAFT);
        scheduleRepository.save(schedule);

        List<Shift> shifts = new ArrayList<>();
        List<ShiftRequirement> requirements = new ArrayList<>();

        for (int dayOffset = 0; dayOffset < DAYS_IN_WEEK; dayOffset++) {
            LocalDate date = request.weekStart().plusDays(dayOffset);

            for (ShiftTemplate template : request.shifts()) {
                Shift shift = new Shift();
                shift.setSchedule(schedule);
                shift.setShiftDate(date);
                shift.setShiftType(shiftTypes.get(template.shiftTypeId()));
                shifts.add(shift);
            }
        }

        shiftRepository.saveAll(shifts);

        int index = 0;
        for (int dayOffset = 0; dayOffset < DAYS_IN_WEEK; dayOffset++) {
            for (ShiftTemplate template : request.shifts()) {
                Shift shift = shifts.get(index++);

                for (RequirementSpec spec : template.requirements()) {
                    if (spec.requiredCount() == 0) {
                        continue;
                    }

                    ShiftRequirement requirement = new ShiftRequirement();
                    requirement.setShift(shift);
                    requirement.setJobPosition(positions.get(spec.jobPositionId()));
                    requirement.setRequiredCount(spec.requiredCount());
                    requirements.add(requirement);
                }
            }
        }

        requirementRepository.saveAll(requirements);

        return findById(schedule.getId());
    }

    @Transactional
    public ScheduleDetailResponse publish(Long id) {
        Schedule schedule = requireSchedule(id);

        if (schedule.getStatus() == ScheduleStatus.PUBLISHED) {
            throw new ConflictException("Schedule " + id + " is already published");
        }

        schedule.setStatus(ScheduleStatus.PUBLISHED);

        return findById(id);
    }

    @Transactional
    public void delete(Long id) {
        Schedule schedule = requireSchedule(id);

        if (schedule.getStatus() == ScheduleStatus.PUBLISHED) {
            throw new ConflictException("A published schedule cannot be deleted");
        }

        scheduleRepository.delete(schedule);
    }

    @Transactional
    public ShiftResponse replaceRequirements(Long scheduleId,
                                             Long shiftId,
                                             ShiftRequirementsUpdateRequest request) {
        Schedule schedule = requireSchedule(scheduleId);

        if (schedule.getStatus() == ScheduleStatus.PUBLISHED) {
            throw new ConflictException("A published schedule cannot be modified");
        }

        Shift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift " + shiftId + " not found"));

        if (!shift.getSchedule().getId().equals(scheduleId)) {
            throw new ValidationException("Shift " + shiftId + " does not belong to schedule " + scheduleId);
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
                .map(spec -> {
                    ShiftRequirement requirement = new ShiftRequirement();
                    requirement.setShift(shift);
                    requirement.setJobPosition(positions.get(spec.jobPositionId()));
                    requirement.setRequiredCount(spec.requiredCount());
                    return requirement;
                })
                .toList();

        requirementRepository.saveAll(replacements);

        return toShiftResponse(shift, replacements);
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

    private Schedule requireSchedule(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule " + id + " not found"));
    }

    private Schedule requireVisible(Long id) {
        Schedule schedule = requireSchedule(id);

        if (!currentUser.isManager() && schedule.getStatus() != ScheduleStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Schedule " + id + " not found");
        }

        return schedule;
    }

    private ShiftResponse toShiftResponse(Shift shift, List<ShiftRequirement> requirements) {
        ShiftType type = shift.getShiftType();

        List<RequirementResponse> requirementResponses = requirements.stream()
                .map(requirement -> new RequirementResponse(
                        requirement.getId(),
                        requirement.getJobPosition().getId(),
                        requirement.getJobPosition().getName(),
                        requirement.getRequiredCount()))
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