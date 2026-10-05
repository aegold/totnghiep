package com.thesis.elearning.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing, declared apart from {@link SecurityConfig}.
 *
 * <p>## Why this is its own class
 * {@code SecurityConfig} needs the encoder to build its
 * {@code DaoAuthenticationProvider}. Declaring the {@code PasswordEncoder} bean
 * inside {@code SecurityConfig} would make the class depend on a bean it
 * defines itself, and the container would fail to start with an unresolvable
 * circular reference. Extracting it breaks the cycle.
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * BCrypt at strength 12.
     *
     * <p>Each increment doubles the work factor, so this is the last cheap
     * moment to raise it; the column is {@code varchar(255)}, which comfortably
     * fits the 60-character BCrypt hash it produces.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}