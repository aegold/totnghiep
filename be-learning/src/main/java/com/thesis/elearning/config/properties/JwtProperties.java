package com.thesis.elearning.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe binding for the {@code app.jwt.*} configuration block.
 *
 * <p>Validation runs at startup so a missing or malformed secret fails the
 * boot rather than the first login attempt.
 *
 * @param secretKey               Base64-encoded 256-bit HMAC key.
 * @param accessTokenExpiration   Access token lifetime in milliseconds (15 min).
 * @param refreshTokenExpiration  Refresh token lifetime in milliseconds (7 days).
 * @param issuer                  {@code iss} claim, verified on every parse.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secretKey,
        @Positive long accessTokenExpiration,
        @Positive long refreshTokenExpiration,
        @NotBlank String issuer) {

    /** {@code expiresAt} for the login/register response, ISO-8601. */
    public String accessTokenExpiresAt() {
        return java.time.Instant.now()
                .plusMillis(accessTokenExpiration)
                .toString();
    }
}