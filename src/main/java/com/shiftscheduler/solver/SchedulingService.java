package com.shiftscheduler.solver;

import ai.timefold.solver.core.api.solver.SolverManager;
import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.domain.ScheduleStatus;
import com.shiftscheduler.schedule.ScheduleGuard;
import com.shiftscheduler.web.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;

// Runs the solver on one week and stores the result.
//
// Deliberately synchronous for now: the request waits for the solve to finish.
// Moving it onto a JMS queue comes later, and this is the service that will
// sit behind the consumer.
@Service
public class SchedulingService {

    private static final Logger log = LoggerFactory.getLogger(SchedulingService.class);

    private final SolverManager<EmployeeSchedule> solverManager;
    private final ScheduleLoader loader;
    private final ScheduleSaver saver;
    private final ScheduleGuard guard;

    public SchedulingService(SolverManager<EmployeeSchedule> solverManager,
                             ScheduleLoader loader,
                             ScheduleSaver saver,
                             ScheduleGuard guard) {
        this.solverManager = solverManager;
        this.loader = loader;
        this.saver = saver;
        this.guard = guard;
    }

    public SolveResponse solve(Long scheduleId) {
        requireDraft(scheduleId);

        EmployeeSchedule problem = loader.load(scheduleId);

        long pinned = problem.getSlots().stream().filter(ShiftSlot::isPinned).count();

        // The one line that says what actually went in. If a result looks
        // wrong, this tells you whether the loader or the solver is to blame.
        log.info("Solving schedule {}: {} slots ({} already assigned), {} employees, "
                        + "{} days of leave, {} stated constraints",
                scheduleId, problem.getSlots().size(), pinned,
                problem.getEmployees().size(), problem.getUnavailableDays().size(),
                problem.getDislikes().size());

        EmployeeSchedule solution = runSolver(scheduleId, problem);

        int saved = saver.save(solution);

        long stillEmpty = solution.getSlots().stream()
                .filter(slot -> slot.getEmployee() == null)
                .count();

        log.info("Solved schedule {}: score {}, {} new assignments, {} slots left empty",
                scheduleId, solution.getScore(), saved, stillEmpty);

        return new SolveResponse(
                scheduleId,
                String.valueOf(solution.getScore()),
                solution.getSlots().size(),
                (int) pinned,
                saved,
                (int) stillEmpty,
                List.of());
    }

    private EmployeeSchedule runSolver(Long scheduleId, EmployeeSchedule problem) {
        try {
            return solverManager.solve(scheduleId, problem).getFinalBestSolution();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Solving was interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Solving failed", e.getCause());
        }
    }

    // Only a locked week gets solved. While collecting, employees are still
    // submitting; once published, the roster is out and only manual changes
    // make sense.
    private void requireDraft(Long scheduleId) {
        Schedule schedule = guard.require(scheduleId);
        guard.requireStatus(schedule, ScheduleStatus.DRAFT);
    }
}