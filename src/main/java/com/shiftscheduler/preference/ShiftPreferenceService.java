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
import com.shiftscheduler.web.ErrorCode;
import com.shiftscheduler.web.ResourceNotFoundException;
import com.shiftscheduler.web.ValidationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// The constraints employees submit before a week is built.
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

    // A manager can filter by employee. An employee always gets his own.
    @Transactional(readOnly = true)
    public List<ShiftPreferenceResponse> findBySchedule(Long scheduleId, Long employeeId) {
        Long filter = currentUser.isManager() ? employeeId : currentUser.employeeId();

        List<ShiftPreference> preferences = (filter == null)
                ? preferenceRepository.findByShiftScheduleIdOrderByShiftShiftDateAscIdAsc(scheduleId)
                : preferenceRepository.findByShiftScheduleIdAndEmployeeIdOrderByShiftShiftDateAscIdAsc(
                scheduleId, filter);

        return preferences.stream().map(this::toResponse).toList();
    }

    // One constraint per employee per shift.
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

    // Changes the type or the reason.
    @Transactional
    public ShiftPreferenceResponse update(Long id, ShiftPreferenceUpdateRequest request) {
        ShiftPreference preference = requireOwned(id);
        requireSubmissionAllowed(preference.getShift().getSchedule());
        requireCurrentVersion(preference, request.version());

        preference.setType(request.type());
        preference.setReason(trimmed(request.reason()));

        return toResponse(preference);
    }

    // Removes the constraint.
    @Transactional
    public void delete(Long id, Long version) {
        ShiftPreference preference = requireOwned(id);
        requireSubmissionAllowed(preference.getShift().getSchedule());
        requireCurrentVersion(preference, version);

        preferenceRepository.delete(preference);
    }

    // The caller sends back the version it was showing, so an edit made from a
    // second screen or by the manager is not overwritten without anyone knowing.
    private void requireCurrentVersion(ShiftPreference preference, Long expected) {
        if (expected == null || expected != preference.getVersion()) {
            throw new ConflictException(
                    "This constraint was changed by someone else. Reload and try again.",
                    ErrorCode.STALE_VERSION);
        }
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

    // Only a manager may submit for somebody else.
    private Long resolveEmployeeId(Long requested) {
        if (requested == null || requested.equals(currentUser.employeeId())) {
            return currentUser.employeeId();
        }

        if (!currentUser.isManager()) {
            throw new AccessDeniedException("Only a manager can submit preferences for another employee");
        }

        return requested;
    }

    // Somebody else's constraint answers 404. A 403 would confirm it exists.
    private ShiftPreference requireOwned(Long id) {
        ShiftPreference preference = preferenceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Preference " + id + " not found"));

        boolean owner = preference.getEmployee().getId().equals(currentUser.employeeId());

        if (!owner && !currentUser.isManager()) {
            throw new ResourceNotFoundException("Preference " + id + " not found");
        }

        return preference;
    }

    // An empty reason is stored as null.
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
                preference.getReason(),
                preference.getVersion());
    }
}