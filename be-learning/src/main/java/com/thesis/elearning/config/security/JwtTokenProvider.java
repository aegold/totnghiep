package com.thesis.elearning.config.security;

import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.config.properties.JwtProperties;
import com.thesis.elearning.modules.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies the stateless access token, and mints opaque refresh
 * tokens.
 *
 * <p>## Deliberate split between the two token kinds
 * The access token is a self-contained JWT so an ordinary request needs no
 * database round-trip. The refresh token is <em>not</em> a JWT: it is a random
 * opaque string persisted as a SHA-256 digest, because it has to be revocable
 * server-side (sign-out, role change, suspected theft). A signed refresh token
 * cannot be revoked without a denylist, which reintroduces the database lookup
 * that the stateless design was meant to avoid - and would still leave a window
 * in which a stolen token is honoured.
 *
 * <p>## Failure reporting
 * {@link #parseAccessToken} never throws. It returns the claims on success and
 * {@link Optional#empty()} on failure, and the failure reason is recorded on the
 * request by the filter so the entry point can explain what went wrong. A stale
 * token and a forged one are reported differently because the remedy differs:
 * the client renews a session for the first and signs in again for the second.
 */
@Component
public class JwtTokenProvider {

    /** Claim holding the granted authorities, mirroring Spring's naming. */
    public static final String CLAIM_ROLES = "roles";

    /** Claim holding the login handle, for diagnostics and UI display. */
    public static final String CLAIM_USERNAME = "username";

    /** Claim holding the display name. */
    public static final String CLAIM_FULL_NAME = "fullName";

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final SecureRandom random = new SecureRandom();

    public JwtTokenProvider(final JwtProperties properties) {
        this.properties = properties;
        this.signingKey = buildKey(properties.secretKey());
    }

    /**
     * HMAC-SHA256 requires at least a 256-bit key. A shorter key is rejected at
     * startup rather than at first use, otherwise it would surface as an opaque
     * signature failure hours later.
     */
    private static SecretKey buildKey(final String base64Secret) {
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Secret);
        } catch (final IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "app.jwt.secret-key must be a Base64-encoded 256-bit key", ex);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret-key must decode to at least 32 bytes, got "
                            + decoded.length);
        }
        return Keys.hmacShaKeyFor(decoded);
    }

    /** Mints a signed access token carrying the principal and its authorities. */
    public String generateAccessToken(final User user) {
        final Instant now = Instant.now();
        final Instant expiry = now.plusMillis(properties.accessTokenExpiration());

        final List<String> authorities = user.getRoles().stream()
                .map(role -> role.getRoleCode())
                .toList();

        return Jwts.builder()
                .subject(String.valueOf(user.getUserId()))
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_FULL_NAME, user.getFullName())
                .claim(CLAIM_ROLES, authorities)
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /** Absolute expiry reported as {@code AuthResponse.expiresAt}. */
    public Instant accessTokenExpiresAt() {
        return Instant.now().plusMillis(properties.accessTokenExpiration());
    }

    /** Access-token lifetime in seconds, for the RFC 6750 {@code expires_in}. */
    public long accessTokenExpiresInSeconds() {
        return properties.accessTokenExpiration() / 1000L;
    }

    /** A cryptographically random opaque refresh token (256 bits, Base64URL). */
    public String generateRefreshToken() {
        final byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Digest stored in {@code refresh_tokens.token}.
     *
     * <p>SHA-256 rather than a password KDF on purpose: this value is looked up
     * by exact match on a unique index, so there is no brute-force path for a
     * slow KDF to defend against.
     */
    public String hashRefreshToken(final String rawToken) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (final NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the platform specification.
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    /**
     * Verifies signature and expiry.
     *
     * @return the claims, or {@link Optional#empty()} when the token is expired,
     *         forged, malformed, or issued by a different key.
     */
    public Optional<Claims> parseAccessToken(final String token) {
        try {
            return Optional.of(
                    Jwts.parser()
                            .verifyWith(signingKey)
                            .requireIssuer(properties.issuer())
                            .build()
                            .parseSignedClaims(token)
                            .getPayload());
        } catch (final ExpiredJwtException ex) {
            return Optional.empty();
        } catch (final JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * Classifies why a token was rejected.
     *
     * <p>Separated from {@link #parseAccessToken} because the two failures mean
     * different things to a user: a stale token is fixed by renewing the session,
     * a forged one is not. The caller stores this on the request and the entry
     * point renders it.
     *
     * @return {@link ErrorCode#ACCESS_TOKEN_EXPIRED} when the signature was good
     *         and only the clock said no, {@link ErrorCode#INVALID_TOKEN}
     *         otherwise.
     */
    public ErrorCode classifyFailure(final String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token);
            // Verified cleanly: the caller reached here without checking the
            // clock, which cannot happen, but treat it as valid rather than
            // accusing the caller of forgery.
            return ErrorCode.INVALID_TOKEN;
        } catch (final ExpiredJwtException ex) {
            return ErrorCode.ACCESS_TOKEN_EXPIRED;
        } catch (final JwtException | IllegalArgumentException ex) {
            return ErrorCode.INVALID_TOKEN;
        }
    }
}