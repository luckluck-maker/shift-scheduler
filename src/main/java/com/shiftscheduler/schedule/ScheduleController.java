package com.shiftscheduler.schedule;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    @PreAuthorize("hasRole('MANAGER')")
    public ScheduleDetailResponse findById(@PathVariable Long id) {
        return scheduleService.findById(id);
    }

    @GetMapping("/{id}/roster")
    public RosterResponse roster(@PathVariable Long id) {
        return scheduleService.roster(id);
    }

    @GetMapping("/{id}/my-week")
    public MyWeekResponse myWeek(@PathVariable Long id,
                                 @RequestParam(required = false) Long employeeId) {
        return scheduleService.myWeek(id, employeeId);
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

    @PutMapping("/{id}/submission-deadline")
    @PreAuthorize("hasRole('MANAGER')")
    public ScheduleDetailResponse setDeadline(@PathVariable Long id,
                                              @Valid @RequestBody DeadlineRequest request) {
        return scheduleService.setSubmissionDeadline(id, request);
    }

    @PostMapping("/{scheduleId}/requirements/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MANAGER')")
    public void clearRequirements(@PathVariable Long scheduleId,
                                  @Valid @RequestBody ClearShiftsRequest request) {
        scheduleService.clearRequirements(scheduleId, request.shiftIds(), request.version());
    }
}