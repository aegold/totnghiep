package com.thesis.elearning.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.config.properties.JwtProperties;
import com.thesis.elearning.modules.auth.entity.Role;
import com.thesis.elearning.modules.auth.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link JwtTokenProvider}.
 *
 * <p>The behaviour that matters is not "a token round-trips" but that a stale
 * token and a forged token are told apart, because the client renews for one and
 * signs in again for the other.
 */
class JwtTokenProviderTest {

    /** 256-bit key, distinct from the runtime default so mixing is visible. */
    private static final String SECRET = "bXktZXN0LXNlY3JldC1zZWNyZXQta2V5LTMyLWJ5dGVzLW1pbmltdW0=";

    private static final String OTHER_SECRET =
            "b3RoZXItc2VjcmV0LWtleS1zZWNyZXQta2V5LTMyLWJ5dGVzLW1pbmltdW0h";

    private JwtTokenProvider provider;

    private static User user() {
        return User.builder()
                .userId(42L)
                .username("nguyenvana")
                .email("nguyenvana@sv.edu.vn")
                .passwordHash("hash")
                .fullName("Nguyen Van A")
                .active(true)
                .deleted(false)
                .roles(
                        Set.of(
                                Role.builder()
                                        .roleId(1)
                                        .roleCode("ROLE_STUDENT")
                                        .roleName("SV")
                                        .build()))
                .build();
    }

    /** Signs a token with an arbitrary key/issuer/expiry to simulate a stranger. */
    private static String sign(final String secret, final String issuer, final Instant expiry) {
        return Jwts.builder()
                .subject("42")
                .issuer(issuer)
                .expiration(Date.from(expiry))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)))
                .compact();
    }

    @BeforeEach
    void setUp() {
        provider =
                new JwtTokenProvider(new JwtProperties(SECRET, 900_000L, 604_800_000L, "iss"));
    }

    @Test
    @DisplayName("round-trips subject, username and authorities")
    void roundTrip() {
        final var claims = provider.parseAccessToken(provider.generateAccessToken(user())).orElseThrow();

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get(JwtTokenProvider.CLAIM_USERNAME)).isEqualTo("nguyenvana");
        @SuppressWarnings("unchecked")
        final List<Object> roles =
                (List<Object>) claims.get(JwtTokenProvider.CLAIM_ROLES);
        assertThat(roles).containsExactly("ROLE_STUDENT");
    }

    @Test
    @DisplayName("reports an expired token as ACCESS_TOKEN_EXPIRED, not INVALID_TOKEN")
    void expiredIsDistinct() {
        final String expired = sign(SECRET, "iss", Instant.now().minusSeconds(60));

        assertThat(provider.parseAccessToken(expired)).isEmpty();
        assertThat(provider.classifyFailure(expired)).isEqualTo(ErrorCode.ACCESS_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("reports a token signed with another key as INVALID_TOKEN")
    void forgedKeyIsInvalid() {
        final String forged = sign(OTHER_SECRET, "iss", Instant.now().plusSeconds(600));

        assertThat(provider.parseAccessToken(forged)).isEmpty();
        assertThat(provider.classifyFailure(forged)).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("reports garbage as INVALID_TOKEN")
    void garbageIsInvalid() {
        assertThat(provider.parseAccessToken("khong-phai-token")).isEmpty();
        assertThat(provider.classifyFailure("khong-phai-token")).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("rejects a token from a different issuer")
    void wrongIssuer() {
        final String wrong = sign(SECRET, "ban-khong-pha-toi", Instant.now().plusSeconds(600));

        assertThat(provider.parseAccessToken(wrong)).isEmpty();
        assertThat(provider.classifyFailure(wrong)).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("hashes refresh tokens deterministically, never storing the raw value")
    void refreshHash() {
        final String raw = "opaque-token";

        final String digest = provider.hashRefreshToken(raw);

        assertThat(digest).hasSize(64).isEqualTo(provider.hashRefreshToken(raw));
        assertThat(digest).isNotEqualTo(raw);
    }

    @Test
    @DisplayName("rejects a weak secret at construction, not at first use")
    void rejectsWeakSecret() {
        // 16 bytes decodes fine but is below the 256-bit floor for HS256.
        final String weak = "c2hvcnQtc2VjcmV0";

        assertThatThrownBy(
                        () ->
                                new JwtTokenProvider(
                                        new JwtProperties(weak, 900_000L, 604_800_000L, "iss")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("exposes the RFC 6750 metadata the client echoes back")
    void rfcMetadata() {
        assertThat(provider.accessTokenExpiresInSeconds()).isEqualTo(900L);
        assertThat(provider.accessTokenExpiresAt()).isAfter(Instant.now());
    }
}