package com.shiftscheduler.solver;

import com.shiftscheduler.domain.Assignment;
import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.repository.AssignmentRepository;
import com.shiftscheduler.repository.EmployeeRepository;
import com.shiftscheduler.repository.ShiftRepository;
import com.shiftscheduler.schedule.ScheduleGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Writes what the solver came up with back into the database.
// Runs in its own transaction after the one the loader used.
@Service
public class ScheduleSaver {

    private static final Logger log = LoggerFactory.getLogger(ScheduleSaver.class);

    private final AssignmentRepository assignmentRepository;
    private final ShiftRepository shiftRepository;
    private final EmployeeRepository employeeRepository;
    private final ScheduleGuard guard;

    public ScheduleSaver(AssignmentRepository assignmentRepository,
                         ShiftRepository shiftRepository,
                         EmployeeRepository employeeRepository, ScheduleGuard guard) {
        this.assignmentRepository = assignmentRepository;
        this.shiftRepository = shiftRepository;
        this.employeeRepository = employeeRepository;
        this.guard = guard;
    }

    @Transactional
    public int save(EmployeeSchedule solution) {
        List<Assignment> created = new ArrayList<>();

        // Who is still on the staff now, at the moment of writing. The solver
        // has been working for up to a minute from the list it read when it
        // started, and someone switched off in between is not in this one.
        // Deactivating is refused while a week is solving, but that check runs
        // before the solver reads its employees, so a request that slipped in
        // between the two would still be missed. This is the last point where
        // it can be caught.
        Set<Long> stillActive = employeeRepository.findByActiveTrue(Sort.unsorted()).stream()
                .map(Employee::getId)
                .collect(Collectors.toSet());

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

            if (!stillActive.contains(slot.getEmployee().getId())) {
                log.warn("Dropped {} from the solution for schedule {} - "
                                + "they were switched off while it was solving",
                        slot.getEmployee().getId(), solution.getScheduleId());
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

        Schedule schedule = guard.require(solution.getScheduleId());
        guard.markChanged(schedule);

        return created.size();
    }
}