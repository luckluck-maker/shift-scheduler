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

import java.util.List;

@Service
public class LeaveService {

    private final EmployeeLeaveRepository leaveRepository;
    private final EmployeeRepository employeeRepository;

    public LeaveService(EmployeeLeaveRepository leaveRepository,
                        EmployeeRepository employeeRepository) {
        this.leaveRepository = leaveRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public List<LeaveResponse> findAll(Long employeeId) {
        List<EmployeeLeave> leaves = employeeId == null
                ? leaveRepository.findByOrderByStartDateDesc()
                : leaveRepository.findByEmployeeIdOrderByStartDateDesc(employeeId);

        return leaves.stream().map(this::toResponse).toList();
    }

    @Transactional
    public LeaveResponse create(LeaveRequest request) {
        validateRange(request);

        Employee employee = requireEmployee(request.employeeId());

        boolean overlaps = leaveRepository
                .existsByEmployeeIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employee.getId(), request.endDate(), request.startDate());

        if (overlaps) {
            throw new ConflictException("This employee already has leave overlapping those dates");
        }

        EmployeeLeave leave = new EmployeeLeave();
        leave.setEmployee(employee);
        leave.setStartDate(request.startDate());
        leave.setEndDate(request.endDate());
        leave.setType(request.type());

        return toResponse(leaveRepository.save(leave));
    }

    @Transactional
    public LeaveResponse update(Long id, LeaveRequest request) {
        validateRange(request);

        EmployeeLeave leave = requireLeave(id);
        Employee employee = requireEmployee(request.employeeId());

        boolean overlaps = leaveRepository
                .existsByEmployeeIdAndIdNotAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employee.getId(), id, request.endDate(), request.startDate());

        if (overlaps) {
            throw new ConflictException("This employee already has leave overlapping those dates");
        }

        leave.setEmployee(employee);
        leave.setStartDate(request.startDate());
        leave.setEndDate(request.endDate());
        leave.setType(request.type());

        return toResponse(leave);
    }

    @Transactional
    public void delete(Long id) {
        leaveRepository.delete(requireLeave(id));
    }

    private void validateRange(LeaveRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new ValidationException("endDate cannot be before startDate");
        }
    }

    private Employee requireEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + id + " not found"));
    }

    private EmployeeLeave requireLeave(Long id) {
        return leaveRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave " + id + " not found"));
    }

    private LeaveResponse toResponse(EmployeeLeave leave) {
        return new LeaveResponse(
                leave.getId(),
                leave.getEmployee().getId(),
                leave.getEmployee().getFullName(),
                leave.getStartDate(),
                leave.getEndDate(),
                leave.getType().name());
    }
}
