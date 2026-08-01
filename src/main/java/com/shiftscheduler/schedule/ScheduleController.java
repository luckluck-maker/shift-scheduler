package com.shiftscheduler.schedule;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

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

    @GetMapping("/{id}/my-week")
    public MyWeekResponse myWeek(@PathVariable Long id) {
        return scheduleService.myWeek(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ScheduleDetailResponse create(@Valid @RequestBody ScheduleCreateRequest request) {
        return scheduleService.create(request);
    }

    @PutMapping("/{id}/lock")
    @PreAuthorize("hasRole('MANAGER')")
    public ScheduleDetailResponse lock(@PathVariable Long id,
                                       @Valid @RequestBody VersionedRequest request) {
        return scheduleService.lock(id, request);
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasRole('MANAGER')")
    public ScheduleDetailResponse publish(@PathVariable Long id,
                                          @Valid @RequestBody VersionedRequest request) {
        return scheduleService.publish(id, request);
    }

    @PutMapping("/{scheduleId}/shifts/{shiftId}/requirements")
    @PreAuthorize("hasRole('MANAGER')")
    public ShiftResponse replaceRequirements(
            @PathVariable Long scheduleId,
            @PathVariable Long shiftId,
            @Valid @RequestBody ShiftRequirementsUpdateRequest request) {
        return scheduleService.replaceRequirements(scheduleId, shiftId, request);
    }
}