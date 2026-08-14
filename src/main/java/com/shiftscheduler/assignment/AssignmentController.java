package com.shiftscheduler.assignment;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.shiftscheduler.schedule.ClearShiftsRequest;

import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('MANAGER')")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/schedules/{scheduleId}/coverage")
    public List<ShiftCoverage> coverage(@PathVariable Long scheduleId) {
        return assignmentService.coverage(scheduleId);
    }

    @DeleteMapping("/schedules/{scheduleId}/assignments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearAll(@PathVariable Long scheduleId, @RequestParam Long version) {
        assignmentService.clearAll(scheduleId, version);
    }

    @GetMapping("/shifts/{shiftId}/available-employees")
    public List<AvailableEmployeeResponse> availableEmployees(
            @PathVariable Long shiftId,
            @RequestParam(required = false) Long jobPositionId) {
        return assignmentService.availableFor(shiftId, jobPositionId);
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentResponse create(@Valid @RequestBody AssignmentCreateRequest request) {
        return assignmentService.create(request);
    }

    @DeleteMapping("/assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @RequestParam Long version) {
        assignmentService.delete(id, version);
    }

    @PostMapping("/schedules/{scheduleId}/assignments/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearShifts(@PathVariable Long scheduleId,
                            @Valid @RequestBody ClearShiftsRequest request) {
        assignmentService.clearShifts(scheduleId, request.shiftIds(), request.version());
    }
}