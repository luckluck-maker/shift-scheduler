package com.shiftscheduler.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

// Beans the auth code needs but that aren't about the filter chain.
@Configuration
public class SecurityBeansConfig {

    private final SecretKey jwtKey;

    // The secret comes from JWT_SECRET in the environment, with a dev fallback
    // in application.properties. HS256 signs with a 256-bit key, so anything
    // shorter is rejected here rather than at the first login attempt.
    public SecurityBeansConfig(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes for HS256, got " + bytes.length);
        }
        this.jwtKey = new SecretKeySpec(bytes, "HmacSHA256");
    }
    // For password hashing I've chosen Argon2 as it is deliberately slow and memory hungry, which
    // providers a good defense against brute force attacks.
    // The static factory picks the parameters Spring recommends.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    // Same secret signs and verifies. HS256 is symmetric, so one key does both.
    @Bean
    public JwtEncoder jwtEncoder() {
        JWKSource<SecurityContext> source = new ImmutableSecret<>(jwtKey);
        return new NimbusJwtEncoder(source);
    }
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(jwtKey).build();
    }
}