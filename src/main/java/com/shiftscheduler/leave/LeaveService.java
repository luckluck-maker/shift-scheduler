package com.shiftscheduler.leave;

import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.domain.EmployeeLeave;
import com.shiftscheduler.repository.EmployeeLeaveRepository;
import com.shiftscheduler.repository.EmployeeRepository;
import com.shiftscheduler.web.ConflictException;
import com.shiftscheduler.web.ResourceNotFoundException;
import com.shiftscheduler.web.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.shiftscheduler.domain.Assignment;
import com.shiftscheduler.repository.AssignmentRepository;
import com.shiftscheduler.web.ErrorCode;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// Days off. A range is saved as one row per day.
@Service
public class LeaveService {

    // Guards against a mistyped year. create saves a row per day, so a wrong
    // date could write thousands. The database can't catch it - once saved
    // there is no range, only single days.
    private static final int MAX_RANGE_DAYS = 366;

    private final EmployeeLeaveRepository leaveRepository;
    private final EmployeeRepository employeeRepository;
    private final AssignmentRepository assignmentRepository;

    public LeaveService(EmployeeLeaveRepository leaveRepository,
                        EmployeeRepository employeeRepository,
                        AssignmentRepository assignmentRepository) {
        this.leaveRepository = leaveRepository;
        this.employeeRepository = employeeRepository;
        this.assignmentRepository = assignmentRepository;
    }

    // The days are merged back into ranges before they go out.
    @Transactional(readOnly = true)
    public List<LeaveResponse> findAll(Long employeeId) {
        List<EmployeeLeave> days = employeeId == null
                ? leaveRepository.findByOrderByEmployeeIdAscLeaveDateAsc()
                : leaveRepository.findByEmployeeIdOrderByLeaveDateAsc(employeeId);

        return groupIntoRanges(days);
    }

    // Creates leave for a date range.
    // Refused if the employee already has leave or a shift in it.
    @Transactional
    public List<LeaveResponse> create(LeaveRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new ValidationException("endDate cannot be before startDate");
        }

        long length = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;

        if (length > MAX_RANGE_DAYS) {
            throw new ValidationException("A leave range cannot exceed " + MAX_RANGE_DAYS + " days");
        }

        Employee employee = employeeRepository.findByIdAndActiveTrue(request.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee " + request.employeeId() + " not found"));

        List<EmployeeLeave> existing = leaveRepository.findByEmployeeIdAndLeaveDateBetween(
                employee.getId(), request.startDate(), request.endDate());

        if (!existing.isEmpty()) {
            throw new ConflictException(
                    employee.getFullName() + " already has leave on " + existing.getFirst().getLeaveDate());
        }

        // Cannot set up leave if already assigned
        List<Assignment> clashes = assignmentRepository.findByEmployeeIdAndShiftShiftDateBetween(
                employee.getId(), request.startDate(), request.endDate());

        if (!clashes.isEmpty()) {
            throw new ConflictException(employee.getFullName()
                    + " is already assigned to a shift on "
                    + clashes.getFirst().getShift().getShiftDate(),
                    ErrorCode.SHIFT_ASSIGNED);
        }

        List<EmployeeLeave> days = new ArrayList<>();

        for (LocalDate date = request.startDate();
             !date.isAfter(request.endDate());
             date = date.plusDays(1)) {

            EmployeeLeave day = new EmployeeLeave();
            day.setEmployee(employee);
            day.setLeaveDate(date);
            day.setType(request.type());
            days.add(day);
        }

        return groupIntoRanges(leaveRepository.saveAll(days));
    }

    // Removes a single day out of a range.
    @Transactional
    public void deleteDay(Long id) {
        EmployeeLeave day = leaveRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave day " + id + " not found"));

        leaveRepository.delete(day);
    }

    // Removes every day between the two dates.
    @Transactional
    public void deleteRange(Long employeeId, LocalDate from, LocalDate to) {
        List<EmployeeLeave> days =
                leaveRepository.findByEmployeeIdAndLeaveDateBetween(employeeId, from, to);

        if (days.isEmpty()) {
            throw new ResourceNotFoundException("No leave found in that range");
        }

        leaveRepository.deleteAll(days);
    }

    // Leave is stored one day per row. This merges consecutive days of the
    // same type back into a range.
    private List<LeaveResponse> groupIntoRanges(List<EmployeeLeave> days) {
        List<LeaveResponse> ranges = new ArrayList<>();

        List<EmployeeLeave> sorted = days.stream()
                .sorted((a, b) -> {
                    int byEmployee = a.getEmployee().getId().compareTo(b.getEmployee().getId());
                    return byEmployee != 0 ? byEmployee : a.getLeaveDate().compareTo(b.getLeaveDate());
                })
                .toList();

        List<EmployeeLeave> current = new ArrayList<>();

        for (EmployeeLeave day : sorted) {
            if (current.isEmpty() || continuesRange(current.getLast(), day)) {
                current.add(day);
            } else {
                ranges.add(toRange(current));
                current = new ArrayList<>(List.of(day));
            }
        }

        if (!current.isEmpty()) {
            ranges.add(toRange(current));
        }

        return ranges;
    }

    private boolean continuesRange(EmployeeLeave previous, EmployeeLeave candidate) {
        return previous.getEmployee().getId().equals(candidate.getEmployee().getId())
                && previous.getType() == candidate.getType()
                && previous.getLeaveDate().plusDays(1).equals(candidate.getLeaveDate());
    }

    private LeaveResponse toRange(List<EmployeeLeave> days) {
        EmployeeLeave first = days.getFirst();
        EmployeeLeave last = days.getLast();

        return new LeaveResponse(
                first.getEmployee().getId(),
                first.getEmployee().getFullName(),
                first.getLeaveDate(),
                last.getLeaveDate(),
                first.getType().name(),
                days.size(),
                days.stream().map(EmployeeLeave::getId).toList());
    }
}