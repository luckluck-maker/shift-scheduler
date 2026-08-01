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

import java.util.List;

@RestController
@RequestMapping("/api")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/schedules/{scheduleId}/coverage")
    public List<ShiftCoverage> coverage(@PathVariable Long scheduleId) {
        return assignmentService.coverage(scheduleId);
    }

    @GetMapping("/schedules/{scheduleId}/my-shifts")
    public List<AssignmentResponse> myShifts(@PathVariable Long scheduleId) {
        return assignmentService.myAssignments(scheduleId);
    }

    @DeleteMapping("/schedules/{scheduleId}/assignments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MANAGER')")
    public void clearAll(@PathVariable Long scheduleId) {
        assignmentService.clearAll(scheduleId);
    }

    @GetMapping("/shifts/{shiftId}/available-employees")
    @PreAuthorize("hasRole('MANAGER')")
    public List<AvailableEmployeeResponse> availableEmployees(
            @PathVariable Long shiftId,
            @RequestParam(required = false) Long jobPositionId) {
        return assignmentService.availableFor(shiftId, jobPositionId);
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public AssignmentResponse create(@Valid @RequestBody AssignmentCreateRequest request) {
        return assignmentService.create(request);
    }

    @DeleteMapping("/assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MANAGER')")
    public void delete(@PathVariable Long id) {
        assignmentService.delete(id);
    }
}