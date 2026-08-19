package com.shiftscheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;


// Everything about who can reach what. Runs before any controller does.

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationConverter jwtAuthenticationConverter)
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
                                "/assets/**", "/vite.svg")
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

    // The token says "role": "MANAGER" and Spring wants ROLE_MANAGER, so this
    // bridges the two for every hasRole check.
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}