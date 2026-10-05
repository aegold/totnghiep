package com.thesis.elearning.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.config.properties.JwtProperties;
import com.thesis.elearning.config.security.JwtTokenProvider;
import com.thesis.elearning.modules.auth.dto.AuthResponse;
import com.thesis.elearning.modules.auth.dto.LoginRequest;
import com.thesis.elearning.modules.auth.dto.RefreshTokenRequest;
import com.thesis.elearning.modules.auth.dto.RegisterRequest;
import com.thesis.elearning.modules.auth.entity.RefreshToken;
import com.thesis.elearning.modules.auth.entity.Role;
import com.thesis.elearning.modules.auth.entity.User;
import com.thesis.elearning.modules.auth.repository.RefreshTokenRepository;
import com.thesis.elearning.modules.auth.repository.RoleRepository;
import com.thesis.elearning.modules.auth.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for {@link AuthService}.
 *
 * <p>The emphasis is on security-relevant orderings rather than happy-path
 * coverage: whether the password is checked before the account status, whether a
 * rotated token can be replayed, and whether a public caller can influence a
 * role.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final String HASH = "$2a$12$abcdefghijklmnopqrstuv";

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        final JwtProperties properties =
                new JwtProperties(
                        "MC0wOCV4egCP1Us+hOx/EnAQ5kH6NqfPIOBUxr7PpwE=",
                        900_000L,
                        604_800_000L,
                        "adaptive-learning-api");

        authService =
                new AuthService(
                        userRepository,
                        roleRepository,
                        refreshTokenRepository,
                        passwordEncoder,
                        tokenProvider,
                        properties);

        lenient().when(passwordEncoder.encode(any())).thenReturn(HASH);
        lenient().when(tokenProvider.generateAccessToken(any())).thenReturn("access.jwt.token");
        // A counter, not a fixed pair: every session must mint a DIFFERENT
        // refresh token, and a test that reuses the presented value would fail
        // to prove rotation actually happened.
        final java.util.concurrent.atomic.AtomicInteger counter =
                new java.util.concurrent.atomic.AtomicInteger();
        lenient()
                .when(tokenProvider.generateRefreshToken())
                .thenAnswer(inv -> "fresh-refresh-" + counter.incrementAndGet());
        lenient().when(tokenProvider.hashRefreshToken(any()))
                .thenAnswer(inv -> "hash:" + inv.getArgument(0, String.class));
        lenient().when(tokenProvider.accessTokenExpiresAt())
                .thenReturn(Instant.parse("2026-01-01T00:15:00Z"));
        lenient().when(tokenProvider.accessTokenExpiresInSeconds()).thenReturn(900L);
    }

    private User user(final boolean active) {
        final Role role =
                Role.builder().roleId(1).roleCode("ROLE_STUDENT").roleName("SV").build();
        return User.builder()
                .userId(7L)
                .username("nguyenvana")
                .email("nguyenvana@sv.edu.vn")
                .passwordHash(HASH)
                .fullName("Nguyen Van A")
                .active(active)
                .deleted(false)
                .roles(Set.of(role))
                .build();
    }

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("returns the nested contract the client destructures")
        void successShape() {
            when(userRepository.findByUsernameOrEmail("nguyenvana"))
                    .thenReturn(Optional.of(user(true)));
            when(passwordEncoder.matches("Matkhau@2026", HASH)).thenReturn(true);

            final AuthResponse response =
                    authService.login(new LoginRequest("nguyenvana", "Matkhau@2026"));

            assertThat(response.user().userId()).isEqualTo(7L);
            assertThat(response.user().roles()).containsExactly("ROLE_STUDENT");
            // The client does `const { user, tokens } = await login(...)`.
            assertThat(response.tokens().accessToken()).isEqualTo("access.jwt.token");
            assertThat(response.tokens().refreshToken()).startsWith("fresh-refresh-");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(900L);
            assertThat(response.expiresAt()).isNotNull();
        }

        @Test
        @DisplayName("rejects an unknown account with the generic message")
        void unknownAccount() {
            when(userRepository.findByUsernameOrEmail("khongco")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginRequest("khongco", "x")))
                    .isInstanceOf(AppException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((AppException) ex).getErrorCode())
                                            .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        }

        @Test
        @DisplayName("rejects a wrong password without disclosing account status")
        void wrongPassword() {
            // Inactive account, but the caller does not know the password: the
            // answer must stay generic, or the endpoint enumerates accounts.
            when(userRepository.findByUsernameOrEmail("nguyenvana"))
                    .thenReturn(Optional.of(user(false)));
            when(passwordEncoder.matches("SAI", HASH)).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest("nguyenvana", "SAI")))
                    .isInstanceOf(AppException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((AppException) ex).getErrorCode())
                                            .isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        }

        @Test
        @DisplayName("verifies the password BEFORE reporting a disabled account")
        void disabledAccountCheckedAfterPassword() {
            when(userRepository.findByUsernameOrEmail("nguyenvana"))
                    .thenReturn(Optional.of(user(false)));
            when(passwordEncoder.matches("Matkhau@2026", HASH)).thenReturn(true);

            assertThatThrownBy(
                            () -> authService.login(new LoginRequest("nguyenvana", "Matkhau@2026")))
                    .isInstanceOf(AppException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((AppException) ex).getErrorCode())
                                            .isEqualTo(ErrorCode.ACCOUNT_DISABLED));

            // The password must be evaluated even though the account is
            // disabled; that ordering is the entire point.
            verify(passwordEncoder).matches("Matkhau@2026", HASH);
        }

        @Test
        @DisplayName("ACCOUNT_DISABLED is 403 so the client stops refreshing")
        void disabledIsForbidden() {
            assertThat(ErrorCode.ACCOUNT_DISABLED.httpStatus().value()).isEqualTo(403);
        }
    }

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("always assigns ROLE_STUDENT")
        void alwaysStudent() {
            when(userRepository.existsByUsername("nguyenvana")).thenReturn(false);
            when(userRepository.existsByEmail("a@sv.edu.vn")).thenReturn(false);
            when(roleRepository.findByRoleCode("ROLE_STUDENT"))
                    .thenReturn(
                            Optional.of(
                                    Role.builder().roleId(1).roleCode("ROLE_STUDENT").build()));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.register(
                    new RegisterRequest(
                            "nguyenvana", "a@sv.edu.vn", "Matkhau@2026", "Nguyen Van A"));

            final ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRoles())
                    .singleElement()
                    .extracting(Role::getRoleCode)
                    .isEqualTo("ROLE_STUDENT");
        }

        @Test
        @DisplayName("RegisterRequest has no role field, closing the escalation hole")
        void noRoleFieldOnPublicRegistration() {
            assertThat(RegisterRequest.class.getRecordComponents())
                    .extracting(component -> component.getName())
                    .containsExactly("username", "email", "password", "fullName");
        }

        @Test
        @DisplayName("rejects a duplicate username")
        void duplicateUsername() {
            when(userRepository.existsByUsername("nguyenvana")).thenReturn(true);

            assertThatThrownBy(
                            () ->
                                    authService.register(
                                            new RegisterRequest(
                                                    "nguyenvana",
                                                    "a@sv.edu.vn",
                                                    "Matkhau@2026",
                                                    "A")))
                    .isInstanceOf(AppException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((AppException) ex).getErrorCode())
                                            .isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("refresh()")
    class Refresh {

        private RefreshToken stored(final boolean revoked, final boolean expired) {
            return RefreshToken.builder()
                    .tokenId(1L)
                    .user(user(true))
                    .token("hash:opaque-refresh")
                    .expiryDate(
                            expired ? LocalDateTime.now().minusDays(1) : LocalDateTime.now().plusDays(1))
                    .revoked(revoked)
                    .build();
        }

        @Test
        @DisplayName("rotates: revokes the presented token and issues a different one")
        void rotates() {
            final RefreshToken token = stored(false, false);
            when(refreshTokenRepository.findByTokenForUpdate("hash:opaque-refresh"))
                    .thenReturn(Optional.of(token));

            final AuthResponse response =
                    authService.refresh(new RefreshTokenRequest("opaque-refresh"));

            // The presented token must no longer be usable.
            assertThat(token.isRevoked()).isTrue();

            // And the replacement must differ, otherwise the client would keep
            // replaying the same value and rotation would buy nothing.
            assertThat(response.tokens().refreshToken())
                    .isNotEqualTo("opaque-refresh")
                    .startsWith("fresh-refresh-");

            final ArgumentCaptor<RefreshToken> captor =
                    ArgumentCaptor.forClass(RefreshToken.class);
            // Two writes: the revoked original, then the replacement row.
            verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(captor.capture());

            final RefreshToken replacement =
                    captor.getAllValues().stream()
                            .filter(saved -> !saved.equals(token))
                            .findFirst()
                            .orElseThrow();

            assertThat(replacement.getToken())
                    .isNotEqualTo("hash:opaque-refresh")
                    .isEqualTo("hash:" + response.tokens().refreshToken());
            assertThat(replacement.isRevoked()).isFalse();
        }

        @Test
        @DisplayName("refuses a token that was already rotated - replay is blocked")
        void replayRejected() {
            when(refreshTokenRepository.findByTokenForUpdate("hash:opaque-refresh"))
                    .thenReturn(Optional.of(stored(true, false)));

            assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("opaque-refresh")))
                    .isInstanceOf(AppException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((AppException) ex).getErrorCode())
                                            .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
        }

        @Test
        @DisplayName("refuses an expired token and deletes the dead row")
        void expiredRejected() {
            final RefreshToken token = stored(false, true);
            when(refreshTokenRepository.findByTokenForUpdate("hash:opaque-refresh"))
                    .thenReturn(Optional.of(token));

            assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("opaque-refresh")))
                    .isInstanceOf(AppException.class);
            verify(refreshTokenRepository).delete(token);
        }

        @Test
        @DisplayName("refuses an unknown token")
        void unknownToken() {
            when(refreshTokenRepository.findByTokenForUpdate(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("khongco")))
                    .isInstanceOf(AppException.class)
                    .satisfies(
                            ex ->
                                    assertThat(((AppException) ex).getErrorCode())
                                            .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
        }
    }

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("revokes the presented token")
        void revokesToken() {
            final RefreshToken token =
                    RefreshToken.builder()
                            .tokenId(1L)
                            .user(user(true))
                            .token("hash:opaque-refresh")
                            .expiryDate(LocalDateTime.now().plusDays(1))
                            .revoked(false)
                            .build();
            when(refreshTokenRepository.findByToken("hash:opaque-refresh"))
                    .thenReturn(Optional.of(token));

            authService.logout("opaque-refresh");

            assertThat(token.isRevoked()).isTrue();
        }

        @Test
        @DisplayName("is a no-op for an unknown token, so sign-out never fails")
        void unknownTokenIsNoop() {
            when(refreshTokenRepository.findByToken(any())).thenReturn(Optional.empty());

            authService.logout("khong-co-that");

            verify(refreshTokenRepository, never()).save(any());
        }
    }
}
