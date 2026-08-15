package com.shiftscheduler.employee;

import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.domain.JobPosition;
import com.shiftscheduler.domain.Role;
import com.shiftscheduler.repository.EmployeeRepository;
import com.shiftscheduler.repository.JobPositionRepository;
import com.shiftscheduler.web.ConflictException;
import com.shiftscheduler.web.ErrorCode;
import com.shiftscheduler.web.ResourceNotFoundException;
import com.shiftscheduler.web.ValidationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.shiftscheduler.domain.Assignment;
import com.shiftscheduler.domain.ScheduleStatus;
import com.shiftscheduler.repository.AssignmentRepository;
import com.shiftscheduler.schedule.ScheduleGuard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final JobPositionRepository jobPositionRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    private final AssignmentRepository assignmentRepository;
    private final ScheduleGuard guard;

    public EmployeeService(EmployeeRepository employeeRepository,
                           JobPositionRepository jobPositionRepository,
                           PasswordEncoder passwordEncoder,
                           AssignmentRepository assignmentRepository, ScheduleGuard guard) {
        this.employeeRepository = employeeRepository;
        this.jobPositionRepository = jobPositionRepository;
        this.passwordEncoder = passwordEncoder;
        this.assignmentRepository = assignmentRepository;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> findAll() {
        // Active first, then by name.
        return employeeRepository.findAll(
                        Sort.by(Sort.Order.desc("active"), Sort.Order.asc("fullName")))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public EmployeeResponse create(EmployeeCreateRequest request) {
        String username = request.username().trim();

        if (employeeRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Username '" + username + "' is already taken");
        }

        Employee employee = new Employee();
        employee.setUsername(username);
        employee.setPasswordHash(passwordEncoder.encode(request.password()));
        employee.setFullName(request.fullName().trim());
        employee.setRole(request.role());
        employee.setMaxWeeklyHours(request.maxWeeklyHours());
        employee.setActive(true);
        employee.setJobPosition(requirePosition(request.jobPositionId()));

        return toResponse(employeeRepository.save(employee));
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
        Employee employee = require(id);

        requireCurrentVersion(employee, request.version());

        boolean losingManager =
                employee.getRole() == Role.MANAGER
                        && (request.role() != Role.MANAGER || !request.active());

        if (losingManager) {
            guardLastManager(employee.getId());
        }

        employee.setFullName(request.fullName().trim());
        employee.setRole(request.role());
        employee.setMaxWeeklyHours(request.maxWeeklyHours());
        employee.setActive(request.active());
        employee.setJobPosition(requirePosition(request.jobPositionId()));

        return toResponse(employee);
    }

    @Transactional
    public void deactivate(Long id, Long version) {
        Employee employee = require(id);
        requireCurrentVersion(employee, version);

        if (employee.getRole() == Role.MANAGER && employee.isActive()) {
            guardLastManager(employee.getId());
        }

        employee.setActive(false);
        releaseFromDrafts(employee);
    }

    @Transactional
    public void changePassword(Long id, PasswordChangeRequest request) {
        Employee employee = require(id);
        employee.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    // A PUT sends every field, including ones the user didn't touch.
    // Without this check, fixing a name would also write back the old hours
    // and undo someone else's change.
    private void requireCurrentVersion(Employee employee, Long expected) {
        if (expected != employee.getVersion()) {
            throw new ConflictException(
                    "This employee was changed by someone else. Reload and try again.",
                    ErrorCode.STALE_VERSION);
        }
    }

    // Reads under a write lock so a second request demoting another manager
    // waits and then sees the real count.
    private void guardLastManager(Long excludedId) {
        long remaining = employeeRepository.lockActiveByRole(Role.MANAGER).stream()
                .filter(manager -> !manager.getId().equals(excludedId))
                .count();

        if (remaining == 0) {
            throw new ValidationException(
                    "The system must keep at least one active manager",
                    ErrorCode.LAST_MANAGER);
        }
    }

    private Employee require(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + id + " not found"));
    }

    private JobPosition requirePosition(Long id) {
        return jobPositionRepository.findById(id)
                .filter(JobPosition::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Job position " + id + " not found"));
    }

    private EmployeeResponse toResponse(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getFullName(),
                employee.getUsername(),
                employee.getRole().name(),
                employee.getMaxWeeklyHours(),
                employee.isActive(),
                employee.getJobPosition().getId(),
                employee.getJobPosition().getName(),
                employee.getVersion());
    }

    // remove deactivated employee from all active drafts
    private void releaseFromDrafts(Employee employee) {
        List<Assignment> assignments = assignmentRepository
                .findByEmployeeIdAndShiftScheduleStatusIn(employee.getId(),
                        List.of(ScheduleStatus.DRAFT, ScheduleStatus.SOLVING));

        if (assignments.isEmpty()) {
            return;
        }

        for (Assignment assignment : assignments) {
            guard.markChanged(assignment.getShift().getSchedule());
        }

        assignmentRepository.deleteAll(assignments);

        log.info("Disabled {} and removed {} assignment(s) from draft weeks",
                employee.getFullName(), assignments.size());
    }
}