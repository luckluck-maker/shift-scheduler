package com.shiftscheduler.auth;

import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.repository.EmployeeRepository;
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
            throw new InvalidCredentialsException("Account is disabled");
        }

        LoginResponse user = new LoginResponse(
                employee.getId(),
                employee.getUsername(),
                employee.getFullName(),
                employee.getRole().name()
        );

        return new LoginResult(issueToken(employee), user);
    }

    private String issueToken(Employee employee) {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("shift-scheduler")
                .issuedAt(now)
                .expiresAt(now.plus(expirationMinutes, ChronoUnit.MINUTES))
                .subject(employee.getUsername())
                .claim("employeeId", employee.getId())
                .claim("role", employee.getRole().name())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}