package com.thesis.elearning.modules.auth.service;

import com.thesis.elearning.config.security.JwtTokenProvider;
import com.thesis.elearning.modules.auth.dto.AuthResponse;
import com.thesis.elearning.modules.auth.dto.AuthTokens;
import com.thesis.elearning.modules.auth.dto.LoginRequest;
import com.thesis.elearning.modules.auth.dto.RefreshTokenRequest;
import com.thesis.elearning.modules.auth.dto.RegisterRequest;
import com.thesis.elearning.modules.auth.dto.UserProfileDto;
import com.thesis.elearning.modules.auth.entity.RefreshToken;
import com.thesis.elearning.modules.auth.entity.Role;
import com.thesis.elearning.modules.auth.entity.User;
import com.thesis.elearning.modules.auth.repository.RefreshTokenRepository;
import com.thesis.elearning.modules.auth.repository.RoleRepository;
import com.thesis.elearning.modules.auth.repository.UserRepository;
import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.config.properties.JwtProperties;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign-in, registration and silent renewal. */
@Service
public class AuthService {

    /** Role granted to self-registered accounts. */
    static final String DEFAULT_ROLE_CODE = "ROLE_STUDENT";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;

    public AuthService(
            final UserRepository userRepository,
            final RoleRepository roleRepository,
            final RefreshTokenRepository refreshTokenRepository,
            final PasswordEncoder passwordEncoder,
            final JwtTokenProvider tokenProvider,
            final JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Verifies credentials and issues a token pair.
     *
     * <h2>Why the password is checked before the account status</h2>
     * The order of these two checks is a security property, not a style choice.
     * An earlier version short-circuited on {@code isActive} first, so a disabled
     * account returned its own error <em>without the password ever being
     * verified</em> - a free account-existence oracle. Verifying first means a
     * caller who does not know the password learns nothing, while a legitimate
     * user whose account was disabled still gets the actionable message.
     */
    @Transactional
    public AuthResponse login(final LoginRequest request) {
        final User user =
                userRepository
                        .findByUsernameOrEmail(request.username().trim())
                        .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        // Password proven, so disclosing account status is now safe.
        if (!user.isLoginAllowed()) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }

        return issueSession(user);
    }

    @Transactional
    public AuthResponse register(final RegisterRequest request) {
        final String username = request.username().trim();
        final String email = request.email().trim();

        if (userRepository.existsByUsername(username)) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "Tên đăng nhập đã tồn tại");
        }
        if (userRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "Địa chỉ email đã được sử dụng");
        }

        final Role role =
                roleRepository
                        .findByRoleCode(DEFAULT_ROLE_CODE)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.INTERNAL_SERVER_ERROR,
                                                "Không tìm thấy vai trò mặc định cho tài khoản mới"));

        final User user =
                User.builder()
                        .username(username)
                        .email(email)
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .fullName(request.fullName().trim())
                        .active(true)
                        .deleted(false)
                        .roles(Set.of(role))
                        .build();

        return issueSession(userRepository.save(user));
    }

    @Transactional
    public AuthResponse refresh(final RefreshTokenRequest request) {
        final String digest = tokenProvider.hashRefreshToken(request.refreshToken());

        /*
         * Locking read. Rotation below is a read-decide-write, so the row must be
         * held for the rest of the transaction: without the lock, two concurrent
         * refreshes carrying the same token both observe revoked = false and both
         * issue a session, and a replayed stolen token would succeed instead of
         * being rejected. The second caller blocks here, resumes once the first
         * commits, sees revoked = true, and fails the check below with a 401.
         */
        final RefreshToken stored =
                refreshTokenRepository
                        .findByTokenForUpdate(digest)
                        .orElseThrow(() -> new AppException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!stored.isUsableAt(LocalDateTime.now())) {
            // Expired or already rotated: drop the row so dead tokens do not
            // accumulate in the table.
            refreshTokenRepository.delete(stored);
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        final User user = stored.getUser();
        if (!user.isLoginAllowed()) {
            // Refreshed credentials prove identity, so this is the safe point to
            // disclose that the account itself is disabled.
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }

        /*
         * Rotate: the presented token is revoked and a new one issued. A stolen
         * token replayed after the legitimate client has refreshed still resolves
         * to a row, but that row is already revoked, so the check above rejects
         * it.
         */
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueSession(user);
    }

    @Transactional
    public void logout(final String rawRefreshToken) {
        final String digest = tokenProvider.hashRefreshToken(rawRefreshToken);
        refreshTokenRepository
                .findByToken(digest)
                .ifPresent(
                        token -> {
                            token.setRevoked(true);
                            refreshTokenRepository.save(token);
                        });
    }

    /** Revokes every live token for a user (sign-out without a token body). */
    @Transactional
    public void logoutAll(final Long userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    /** Current principal, for {@code GET /auth/me}. */
    @Transactional(readOnly = true)
    public UserProfileDto currentUser(final Long userId) {
        return userRepository
                .findById(userId)
                .map(UserProfileDto::from)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** Mints an access token plus a fresh, persisted refresh token. */
    private AuthResponse issueSession(final User user) {
        final String accessToken = tokenProvider.generateAccessToken(user);
        final String refreshToken = tokenProvider.generateRefreshToken();

        refreshTokenRepository.save(
                RefreshToken.builder()
                        .user(user)
                        .token(tokenProvider.hashRefreshToken(refreshToken))
                        .expiryDate(
                                LocalDateTime.ofInstant(
                                        Instant.now()
                                                .plusMillis(
                                                        jwtProperties.refreshTokenExpiration()),
                                        ZoneOffset.UTC))
                        .revoked(false)
                        .build());

        return new AuthResponse(
                UserProfileDto.from(user),
                new AuthTokens(accessToken, refreshToken),
                tokenProvider.accessTokenExpiresAt(),
                AuthResponse.BEARER,
                tokenProvider.accessTokenExpiresInSeconds());
    }
}