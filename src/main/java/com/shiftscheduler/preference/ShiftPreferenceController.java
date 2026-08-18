package com.shiftscheduler.preference;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// The constraints employees submit.
@RestController
@RequestMapping("/api/shift-preferences")
public class ShiftPreferenceController {

    private final ShiftPreferenceService preferenceService;

    public ShiftPreferenceController(ShiftPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public List<ShiftPreferenceResponse> findBySchedule(
            @RequestParam Long scheduleId,
            @RequestParam(required = false) Long employeeId) {
        return preferenceService.findBySchedule(scheduleId, employeeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShiftPreferenceResponse create(@Valid @RequestBody ShiftPreferenceCreateRequest request) {
        return preferenceService.create(request);
    }

    @PutMapping("/{id}")
    public ShiftPreferenceResponse update(@PathVariable Long id,
                                          @Valid @RequestBody ShiftPreferenceUpdateRequest request) {
        return preferenceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        preferenceService.delete(id);
    }
}
