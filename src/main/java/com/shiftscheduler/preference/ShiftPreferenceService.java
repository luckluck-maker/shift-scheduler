package com.shiftscheduler.preference;

import com.shiftscheduler.auth.CurrentUserProvider;
import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.domain.ScheduleStatus;
import com.shiftscheduler.domain.Shift;
import com.shiftscheduler.domain.ShiftPreference;
import com.shiftscheduler.repository.EmployeeRepository;
import com.shiftscheduler.repository.ShiftPreferenceRepository;
import com.shiftscheduler.repository.ShiftRepository;
import com.shiftscheduler.schedule.ScheduleGuard;
import com.shiftscheduler.web.ConflictException;
import com.shiftscheduler.web.ResourceNotFoundException;
import com.shiftscheduler.web.ValidationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ShiftPreferenceService {

    private final ShiftPreferenceRepository preferenceRepository;
    private final ShiftRepository shiftRepository;
    private final EmployeeRepository employeeRepository;
    private final ScheduleGuard guard;
    private final CurrentUserProvider currentUser;

    public ShiftPreferenceService(ShiftPreferenceRepository preferenceRepository,
                                  ShiftRepository shiftRepository,
                                  EmployeeRepository employeeRepository,
                                  ScheduleGuard guard,
                                  CurrentUserProvider currentUser) {
        this.preferenceRepository = preferenceRepository;
        this.shiftRepository = shiftRepository;
        this.employeeRepository = employeeRepository;
        this.guard = guard;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<ShiftPreferenceResponse> findBySchedule(Long scheduleId, Long employeeId) {
        Long filter = currentUser.isManager() ? employeeId : currentUser.employeeId();

        List<ShiftPreference> preferences = (filter == null)
                ? preferenceRepository.findByShiftScheduleIdOrderByShiftShiftDateAscIdAsc(scheduleId)
                : preferenceRepository.findByShiftScheduleIdAndEmployeeIdOrderByShiftShiftDateAscIdAsc(
                scheduleId, filter);

        return preferences.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ShiftPreferenceResponse create(ShiftPreferenceCreateRequest request) {
        Long employeeId = resolveEmployeeId(request.employeeId());

        Shift shift = shiftRepository.findById(request.shiftId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shift " + request.shiftId() + " not found"));

        requireSubmissionAllowed(shift.getSchedule());

        if (preferenceRepository.existsByEmployeeIdAndShiftId(employeeId, shift.getId())) {
            throw new ConflictException("A preference for this shift already exists");
        }

        Employee employee = employeeRepository.findByIdAndActiveTrue(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee " + employeeId + " not found"));

        ShiftPreference preference = new ShiftPreference();
        preference.setEmployee(employee);
        preference.setShift(shift);
        preference.setType(request.type());
        preference.setReason(trimmed(request.reason()));

        return toResponse(preferenceRepository.save(preference));
    }

    @Transactional
    public ShiftPreferenceResponse update(Long id, ShiftPreferenceUpdateRequest request) {
        ShiftPreference preference = requireOwned(id);
        requireSubmissionAllowed(preference.getShift().getSchedule());

        preference.setType(request.type());
        preference.setReason(trimmed(request.reason()));

        return toResponse(preference);
    }

    @Transactional
    public void delete(Long id) {
        ShiftPreference preference = requireOwned(id);
        requireSubmissionAllowed(preference.getShift().getSchedule());

        preferenceRepository.delete(preference);
    }

    // Employees submit only while the schedule is collecting.
    // The manager can still fix a constraint after the window closed.
    private void requireSubmissionAllowed(Schedule schedule) {
        if (currentUser.isManager()) {
            guard.requireStatus(schedule, ScheduleStatus.COLLECTING, ScheduleStatus.DRAFT);
        } else {
            guard.requireStatus(schedule, ScheduleStatus.COLLECTING);
        }
    }

    private Long resolveEmployeeId(Long requested) {
        if (requested == null || requested.equals(currentUser.employeeId())) {
            return currentUser.employeeId();
        }

        if (!currentUser.isManager()) {
            throw new AccessDeniedException("Only a manager can submit preferences for another employee");
        }

        return requested;
    }

    private ShiftPreference requireOwned(Long id) {
        ShiftPreference preference = preferenceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Preference " + id + " not found"));

        boolean owner = preference.getEmployee().getId().equals(currentUser.employeeId());

        if (!owner && !currentUser.isManager()) {
            throw new ResourceNotFoundException("Preference " + id + " not found");
        }

        return preference;
    }

    private String trimmed(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ShiftPreferenceResponse toResponse(ShiftPreference preference) {
        Shift shift = preference.getShift();
        Employee employee = preference.getEmployee();

        return new ShiftPreferenceResponse(
                preference.getId(),
                shift.getId(),
                shift.getShiftDate(),
                shift.getShiftType().getName(),
                employee.getId(),
                employee.getFullName(),
                preference.getType().name(),
                preference.getReason());
    }
}