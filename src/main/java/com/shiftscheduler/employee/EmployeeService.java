package com.shiftscheduler.employee;

import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.domain.JobPosition;
import com.shiftscheduler.domain.Role;
import com.shiftscheduler.domain.RosterChange;
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
import com.shiftscheduler.repository.RosterChangeRepository;
import com.shiftscheduler.repository.ScheduleRepository;
import com.shiftscheduler.schedule.ScheduleGuard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final JobPositionRepository jobPositionRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    private final AssignmentRepository assignmentRepository;
    private final RosterChangeRepository rosterChangeRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleGuard guard;

    public EmployeeService(EmployeeRepository employeeRepository,
                           JobPositionRepository jobPositionRepository,
                           PasswordEncoder passwordEncoder,
                           AssignmentRepository assignmentRepository,
                           RosterChangeRepository rosterChangeRepository,
                           ScheduleRepository scheduleRepository, ScheduleGuard guard) {
        this.employeeRepository = employeeRepository;
        this.jobPositionRepository = jobPositionRepository;
        this.passwordEncoder = passwordEncoder;
        this.assignmentRepository = assignmentRepository;
        this.rosterChangeRepository = rosterChangeRepository;
        this.scheduleRepository = scheduleRepository;
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

        // Only the role can cost us a manager here now - being switched off is a
        // separate request, and that one does its own check.
        if (employee.getRole() == Role.MANAGER && request.role() != Role.MANAGER) {
            guardLastManager(employee.getId());
        }

        employee.setFullName(request.fullName().trim());
        employee.setRole(request.role());
        employee.setMaxWeeklyHours(request.maxWeeklyHours());
        employee.setJobPosition(requirePosition(request.jobPositionId()));

        return toResponse(employee);
    }

    // The other half of deactivate. Kept as its own call rather than a field on
    // the edit form, because switching someone on or off is a change of state
    // with consequences, not an attribute like their name.
    @Transactional
    public EmployeeResponse activate(Long id, Long version) {
        Employee employee = require(id);
        requireCurrentVersion(employee, version);

        requireNothingSolving();

        // Their old job may have been retired while they were away. Bringing them
        // back onto it would leave an active employee doing a job no shift asks
        // for, so the manager has to give them a current one first.
        if (!employee.getJobPosition().isActive()) {
            throw new ValidationException(
                    "The job position '" + employee.getJobPosition().getName()
                            + "' is no longer in use. Give this employee a current one first.");
        }

        employee.setActive(true);

        return toResponse(employee);
    }

    @Transactional
    public void deactivate(Long id, Long version) {
        Employee employee = require(id);
        requireCurrentVersion(employee, version);

        requireNothingSolving();

        if (employee.getRole() == Role.MANAGER && employee.isActive()) {
            guardLastManager(employee.getId());
        }

        employee.setActive(false);
        releaseFromSchedules(employee);
    }

    @Transactional
    public void changePassword(Long id, PasswordChangeRequest request) {
        Employee employee = require(id);
        employee.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    // The solver holds a week in memory and writes it out when it finishes, using
    // the list of employees it read when it started. Turning someone off in the
    // middle never reaches that list, so they would come back in the solution it
    // saves. Turning someone on is harmless in the same way - the solver will not
    // know about them either - but every other action is refused while a week is
    // solving, and having one exception is what makes rules get forgotten.
    private void requireNothingSolving() {
        if (scheduleRepository.existsByStatus(ScheduleStatus.SOLVING)) {
            throw new ConflictException(
                    "A schedule is being built right now. Wait for it to finish and try again.",
                    ErrorCode.WRONG_STATUS);
        }
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

    // Takes a disabled employee off the weeks that are still being worked on, and
    // undoes anything they were given after a week had already gone out.
    //
    // A week that was published stays as it was published - it is the record of
    // what people were told, so the shifts they already had stay on it. Only the
    // changes made afterwards come off, because those never settled.
    private void releaseFromSchedules(Employee employee) {
        List<Assignment> assignments = new ArrayList<>(assignmentRepository
                .findByEmployeeIdAndShiftScheduleStatusIn(employee.getId(),
                        List.of(ScheduleStatus.DRAFT, ScheduleStatus.SOLVING)));

        int fromDrafts = assignments.size();

        // Every row here is a shift they gained or lost after their week was
        // published, so the ones they gained are exactly what needs undoing.
        List<RosterChange> waiting = rosterChangeRepository.findByEmployeeId(employee.getId());

        List<Long> addedShiftIds = waiting.stream()
                .filter(RosterChange::isAdded)
                .map(change -> change.getShift().getId())
                .toList();

        if (!addedShiftIds.isEmpty()) {
            assignments.addAll(assignmentRepository
                    .findByEmployeeIdAndShiftIdIn(employee.getId(), addedShiftIds));
        }

        // Dropped whichever way it went: there is nobody left to tell.
        rosterChangeRepository.deleteAll(waiting);

        if (assignments.isEmpty()) {
            return;
        }

        for (Assignment assignment : assignments) {
            guard.markChanged(assignment.getShift().getSchedule());
        }

        assignmentRepository.deleteAll(assignments);

        log.info("Disabled {} - removed {} assignment(s) from draft weeks and undid {} "
                        + "change(s) made after a week was published",
                employee.getFullName(), fromDrafts, assignments.size() - fromDrafts);
    }
}