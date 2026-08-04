package com.shiftscheduler.solver;

import com.shiftscheduler.domain.Assignment;
import com.shiftscheduler.repository.AssignmentRepository;
import com.shiftscheduler.repository.EmployeeRepository;
import com.shiftscheduler.repository.ShiftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// Writes what the solver came up with back into the database.
// Runs in its own transaction after the one the loader used.
@Service
public class ScheduleSaver {

    private final AssignmentRepository assignmentRepository;
    private final ShiftRepository shiftRepository;
    private final EmployeeRepository employeeRepository;

    public ScheduleSaver(AssignmentRepository assignmentRepository,
                         ShiftRepository shiftRepository,
                         EmployeeRepository employeeRepository) {
        this.assignmentRepository = assignmentRepository;
        this.shiftRepository = shiftRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public int save(EmployeeSchedule solution) {
        List<Assignment> created = new ArrayList<>();

        for (ShiftSlot slot : solution.getSlots()) {
            // Already a row in the database — the manager put it there.
            if (slot.isPinned()) {
                continue;
            }

            // Nobody was found for this one. No row means no assignment, and
            // the gap shows up in the coverage view instead.
            if (slot.getEmployee() == null) {
                continue;
            }

            Assignment assignment = new Assignment();
            assignment.setShift(shiftRepository.getReferenceById(slot.getShift().getId()));
            assignment.setEmployee(
                    employeeRepository.getReferenceById(slot.getEmployee().getId()));
            assignment.setOverride(false);

            created.add(assignment);
        }

        assignmentRepository.saveAll(created);

        return created.size();
    }
}