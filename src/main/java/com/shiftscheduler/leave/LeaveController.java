package com.shiftscheduler.leave;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaves")
@PreAuthorize("hasRole('MANAGER')")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @GetMapping
    public List<LeaveResponse> findAll(@RequestParam(required = false) Long employeeId) {
        return leaveService.findAll(employeeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveResponse create(@Valid @RequestBody LeaveRequest request) {
        return leaveService.create(request);
    }

    @PutMapping("/{id}")
    public LeaveResponse update(@PathVariable Long id, @Valid @RequestBody LeaveRequest request) {
        return leaveService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        leaveService.delete(id);
    }
}
