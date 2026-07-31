package com.shiftscheduler.shifttype;

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
@RequestMapping("/api/shift-types")
public class ShiftTypeController {

    private final ShiftTypeService shiftTypeService;

    public ShiftTypeController(ShiftTypeService shiftTypeService) {
        this.shiftTypeService = shiftTypeService;
    }

    @GetMapping
    public List<ShiftTypeResponse> findAll() {
        return shiftTypeService.findAll();
    }

    @GetMapping("/{id}")
    public ShiftTypeResponse findById(@PathVariable Long id) {
        return shiftTypeService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ShiftTypeResponse create(@Valid @RequestBody ShiftTypeRequest request) {
        return shiftTypeService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ShiftTypeResponse update(@PathVariable Long id,
                                    @Valid @RequestBody ShiftTypeRequest request) {
        return shiftTypeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MANAGER')")
    public void delete(@PathVariable Long id) {
        shiftTypeService.delete(id);
    }
}