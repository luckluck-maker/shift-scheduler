package com.shiftscheduler.auth;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.repository.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Login, logout, and who am I.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieFactory cookieFactory;
    private final EmployeeRepository employees;

    public AuthController(AuthService authService, AuthCookieFactory cookieFactory,
                          EmployeeRepository employees) {
        this.authService = authService;
        this.cookieFactory = cookieFactory;
        this.employees = employees;
    }

    // The token goes back as a cookie, not in the body, so the browser sends
    // it on its own and JavaScript never sees it.
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletResponse response) {
        LoginResult result = authService.login(request);

        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.issue(result.token()).toString());

        return result.user();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.expire().toString());
    }

    // Reads the employee rather than the token, so a role that changed reaches
    // the screen and the menu matches what the checks allow.
    @GetMapping("/me")
    public CurrentUser me(@AuthenticationPrincipal Jwt jwt) {
        Number id = jwt.getClaim("employeeId");

        Employee employee = employees.findByIdAndActiveTrue(id.longValue())
                // The filter chain already refused a disabled or missing employee.
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated employee " + id + " is gone"));

        return new CurrentUser(
                employee.getId(),
                employee.getUsername(),
                employee.getFullName(),
                employee.getRole().name()
        );
    }
}