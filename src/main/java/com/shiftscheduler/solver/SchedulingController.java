package com.shiftscheduler.solver;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// Starting the solver on a week and asking whether it is still running.
@RestController
@RequestMapping("/api/schedules")
@PreAuthorize("hasRole('MANAGER')")
public class SchedulingController {

    private final SchedulingService schedulingService;

    public SchedulingController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @PostMapping("/{id}/solve")
    public SolveResponse solve(@PathVariable Long id) {
        return schedulingService.solve(id);
    }

    @GetMapping("/{id}/solve-status")
    public SolveStatusResponse status(@PathVariable Long id) {
        return schedulingService.status(id);
    }
}