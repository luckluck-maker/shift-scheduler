package com.shiftscheduler.auth;

import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.repository.EmployeeRepository;
import com.shiftscheduler.web.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

// Checking a password and handing back a signed token.
@Service
public class AuthService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long expirationMinutes;

    public AuthService(EmployeeRepository employeeRepository,
                       PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder,
                       @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.expirationMinutes = expirationMinutes;
    }

    @Transactional(readOnly = true)
    public LoginResult login(LoginRequest request) {
        Optional<Employee> found = employeeRepository.findByUsername(request.username());

        if (found.isEmpty()) {
            // Hashed anyway so a wrong username takes as long as a wrong password.
            // A quick answer would tell an attacker the user doesn't exist.
            passwordEncoder.encode(request.password());
            throw new InvalidCredentialsException("Invalid username or password");
        }

        Employee employee = found.get();

        if (!passwordEncoder.matches(request.password(), employee.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        // Returns a unique deny message to be able to differentiate between inactive user
        // and the incorrect password / username deny.
        // In prod message should be set to the default invalid message to not allow
        // information leak
        if (!employee.isActive()) {
            throw new InvalidCredentialsException(
                    "Account is disabled", ErrorCode.ACCOUNT_DISABLED);
        }

        LoginResponse user = new LoginResponse(
                employee.getId(),
                employee.getUsername(),
                employee.getFullName(),
                employee.getRole().name()
        );

        return new LoginResult(issueToken(employee), user);
    }

    // Reads the employee and not the token, so a role that changed since login
    // reaches the screen.
    @Transactional(readOnly = true)
    public CurrentUser currentUser(Long employeeId) {
        Employee employee = employeeRepository.findByIdAndActiveTrue(employeeId)
                // The filter chain already refused a disabled or missing employee.
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated employee " + employeeId + " is gone"));

        return new CurrentUser(
                employee.getId(),
                employee.getUsername(),
                employee.getFullName(),
                employee.getRole().name()
        );
    }

    // The token carries the id and the role, so a request does not have to
    // load the employee to know who is asking.
    private String issueToken(Employee employee) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("shift-scheduler")
                .issuedAt(now)
                .expiresAt(now.plus(expirationMinutes, ChronoUnit.MINUTES))
                .subject(employee.getUsername())
                .claim("employeeId", employee.getId())
                .claim("role", employee.getRole().name())
                .claim("fullName", employee.getFullName())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}