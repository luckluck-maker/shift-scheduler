package com.shiftscheduler.schedule;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public List<ScheduleSummaryResponse> findAll() {
        return scheduleService.findAll();
    }

    @GetMapping("/{id}")
    public ScheduleDetailResponse findById(@PathVariable Long id) {
        return scheduleService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ScheduleDetailResponse create(@Valid @RequestBody ScheduleCreateRequest request) {
        return scheduleService.create(request);
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasRole('MANAGER')")
    public ScheduleDetailResponse publish(@PathVariable Long id) {
        return scheduleService.publish(id);
    }

    @PutMapping("/{scheduleId}/shifts/{shiftId}/requirements")
    @PreAuthorize("hasRole('MANAGER')")
    public ShiftResponse replaceRequirements(
            @PathVariable Long scheduleId,
            @PathVariable Long shiftId,
            @Valid @RequestBody ShiftRequirementsUpdateRequest request) {
        return scheduleService.replaceRequirements(scheduleId, shiftId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MANAGER')")
    public void delete(@PathVariable Long id) {
        scheduleService.delete(id);
    }
}