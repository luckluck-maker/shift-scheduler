package com.shiftscheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import com.shiftscheduler.domain.Employee;
import com.shiftscheduler.repository.EmployeeRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;


// Everything about who can reach what. Runs before any controller does.

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter)
            throws Exception {
        http
                // The token lives in a SameSite=Strict cookie, so the browser
                // won't send it from another site. That is what CSRF tokens
                // are for, therefore it is not required.
                .csrf(AbstractHttpConfigurer::disable)

                // stateless - each request is a standalone
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // logout is open on purpose: an expired token still
                        // needs a way to clear the cookie.
                        .requestMatchers("/api/health", "/api/auth/login", "/api/auth/logout")
                        .permitAll()

                        // The page itself and the files it is built from. Running
                        // in development the browser gets these from Vite and
                        // only /api comes here, so this only matters once the
                        // client is packaged into the jar - without it the login
                        // page would need a login to reach. Nothing here carries
                        // data; every value on screen comes from an API call that
                        // is still checked below.
                        .requestMatchers("/", "/index.html", "/favicon.ico",
                                "/assets/**", "/favicon.svg")
                        .permitAll()

                        // The client routes. Same list as SpaController, which
                        // forwards them to index.html so a refresh on /schedule
                        // does not 404.
                        .requestMatchers("/{path:^(?!api|assets)[^.]*}",
                                "/{path:^(?!api|assets)[^.]*}/**")
                        .permitAll()

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        // Spring looks in the Authorization header by default.
                        // changing it so it'll look in the cookie.
                        .bearerTokenResolver(new CookieBearerTokenResolver())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                );

        return http.build();
    }

    // Reads the employee on every request, because the token keeps the role from
    // login and would still work after the role changed or the account was
    // disabled. Spring wants ROLE_MANAGER, the row says MANAGER.
    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(
            EmployeeRepository employees) {

        return jwt -> {
            Number id = jwt.getClaim("employeeId");

            Employee employee = employees.findByIdAndActiveTrue(id.longValue())
                    .orElseThrow(() -> new InvalidBearerTokenException(
                            "The account is disabled or no longer exists"));

            return new JwtAuthenticationToken(jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_" + employee.getRole().name())));
        };
    }
}