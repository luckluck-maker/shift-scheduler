package com.shiftscheduler.solver;

import ai.timefold.solver.core.api.solver.SolverManager;
import com.shiftscheduler.domain.Schedule;
import com.shiftscheduler.domain.ScheduleStatus;
import com.shiftscheduler.schedule.ScheduleGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ai.timefold.solver.core.api.solver.SolverStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final SolvePhase phase;

    // Keeps the last failure per week, since the solver runs on its own thread
    // and only solve-status can hand the error back.
    private final Map<Long, String> failures = new ConcurrentHashMap<>();

    public SchedulingService(SolverManager<EmployeeSchedule> solverManager,
                             ScheduleLoader loader,
                             ScheduleSaver saver,
                             ScheduleGuard guard, SolvePhase phase) {
        this.solverManager = solverManager;
        this.loader = loader;
        this.saver = saver;
        this.guard = guard;
        this.phase = phase;
    }

    public SolveResponse solve(Long scheduleId) {

        phase.begin(scheduleId);
        failures.remove(scheduleId);

        try {
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
                    .withExceptionHandler((id, t) -> fail(scheduleId, t))
                    .run();

            return new SolveResponse(
                    scheduleId, "SOLVING",
                    problem.getSlots().size(), (int) pinned);

        } catch (RuntimeException e) {
            // The solver never got the job, so put the week back here.
            phase.end(scheduleId);
            throw e;
        }
    }

    // Catches a failed save the same way as a solver error, since it happens on
    // the solver's thread too.
    private void onSolved(EmployeeSchedule solution) {
        int saved;

        try {
            saved = saver.save(solution);
        } catch (RuntimeException e) {
            fail(solution.getScheduleId(), e);
            return;
        }

        phase.end(solution.getScheduleId());

        long stillEmpty = solution.getSlots().stream()
                .filter(slot -> slot.getEmployee() == null)
                .count();

        log.info("Solved schedule {}: score {}, {} new assignments, {} slots left empty",
                solution.getScheduleId(), solution.getScore(), saved, stillEmpty);
    }

    // Sends the real reason to the log and keeps one fixed message for the screen.
    private void fail(Long scheduleId, Throwable t) {
        log.error("Solving schedule " + scheduleId + " failed", t);
        failures.put(scheduleId, "The roster could not be built. Try again.");
        phase.end(scheduleId);
    }

    // The screen calls this every second while it shows the spinner.
    public SolveStatusResponse status(Long scheduleId) {
        SolverStatus status = solverManager.getSolverStatus(scheduleId);

        return new SolveStatusResponse(
                scheduleId,
                status != SolverStatus.NOT_SOLVING,
                status.name(),
                failures.get(scheduleId));
    }
}
