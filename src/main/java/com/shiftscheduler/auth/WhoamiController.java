package com.shiftscheduler.auth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
// temporarily
@RestController
@RequestMapping("/api/debug")
public class WhoamiController {

    @GetMapping("/manager-only")
    @PreAuthorize("hasRole('MANAGER')")
    public Map<String, String> managerOnly() {
        return Map.of("access", "granted");
    }

    @GetMapping("/employee-only")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public Map<String, String> employeeOnly() {
        return Map.of("access", "granted");
    }
}