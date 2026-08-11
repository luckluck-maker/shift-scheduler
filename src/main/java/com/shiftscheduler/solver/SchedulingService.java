package com.shiftscheduler.solver;

import ai.timefold.solver.core.api.solver.SolverManager;
import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.domain.ScheduleStatus;
import com.shiftscheduler.schedule.ScheduleGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ai.timefold.solver.core.api.solver.SolverStatus;

import java.util.List;

// Runs the solver on one week and stores the result.
// Runs asynchronously: solve() hands the problem to SolverManager and returns "SOLVING"
// when the solver finishes, it saves the result. the screen checks the status every second
// until it changes back to NOT_SOLVING.
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

        log.info("Solving schedule {}: {} slots ({} already assigned), {} employees, "
                        + "{} days of leave, {} stated constraints",
                scheduleId, problem.getSlots().size(), pinned,
                problem.getEmployees().size(), problem.getUnavailableDays().size(),
                problem.getDislikes().size());

        // Hands the problem off and returns. The consumer runs on the solver's
        // own thread once it finishes.
        solverManager.solveBuilder()
                .withProblemId(scheduleId)
                .withProblem(problem)
                .withFinalBestSolutionEventConsumer(event -> onSolved(event.solution()))
                .withExceptionHandler((id, throwable) ->
                        log.error("Solving schedule " + id + " failed", throwable))
                .run();

        return new SolveResponse(
                scheduleId, "SOLVING",
                problem.getSlots().size(), (int) pinned,
                0, 0, List.of());
    }

    private void onSolved(EmployeeSchedule solution) {
        int saved = saver.save(solution);

        long stillEmpty = solution.getSlots().stream()
                .filter(slot -> slot.getEmployee() == null)
                .count();

        log.info("Solved schedule {}: score {}, {} new assignments, {} slots left empty",
                solution.getScheduleId(), solution.getScore(), saved, stillEmpty);
    }


    // Only a locked week gets solved. While collecting, employees are still
    // submitting; once published, the roster is out and only manual changes
    // make sense.
    private void requireDraft(Long scheduleId) {
        Schedule schedule = guard.require(scheduleId);
        guard.requireStatus(schedule, ScheduleStatus.DRAFT);
    }

    // The screen calls this every second while it shows the spinner.
    public SolveStatusResponse status(Long scheduleId) {
        SolverStatus status = solverManager.getSolverStatus(scheduleId);

        return new SolveStatusResponse(
                scheduleId,
                status != SolverStatus.NOT_SOLVING,
                status.name());
    }
}