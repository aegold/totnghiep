package com.thesis.elearning.modules.auth.controller;

import com.thesis.elearning.modules.auth.dto.AuthResponse;
import com.thesis.elearning.modules.auth.dto.LoginRequest;
import com.thesis.elearning.modules.auth.dto.RefreshTokenRequest;
import com.thesis.elearning.modules.auth.dto.RegisterRequest;
import com.thesis.elearning.modules.auth.dto.UserProfileDto;
import com.thesis.elearning.modules.auth.service.AuthService;
import com.thesis.elearning.common.dto.ApiResponse;
import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 *
 * <p>Paths match {@code AUTH_ENDPOINTS} in the client's {@code apiClient.ts}
 * exactly. The context path {@code /api/v1} is supplied by the servlet
 * container, so every mapping below is relative to it.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(final AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody final LoginRequest request) {
        return ApiResponse.success(authService.login(request), "Đăng nhập thành công");
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody final RegisterRequest request) {
        // 201: a resource was created, and the client is signed in immediately.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                authService.register(request),
                                "Đăng ký tài khoản thành công"));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(
            @Valid @RequestBody final RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request), "Đã làm mới phiên làm việc");
    }

    /**
     * Best-effort sign-out.
     *
     * <p>The client calls this during sign-out and deliberately ignores the
     * outcome, clearing its local session either way - see {@code logout()} in
     * the React {@code authApi}. Two consequences shape this endpoint:
     *
     * <ul>
     *   <li>The body is OPTIONAL. The client sends none, so requiring a refresh
     *       token would make every sign-out a 400.</li>
     *   <li>The route is PUBLIC. By sign-out time the access token is often
     *       already expired, and a 401 here would prevent the server from
     *       revoking anything at all.</li>
     * </ul>
     *
     * <p>When a token is supplied it is revoked directly. When it is not, the
     * authenticated principal (if the header survived) has every one of their
     * tokens revoked instead, which still terminates the session.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @Valid @RequestBody(required = false) final RefreshTokenRequest request,
            final Principal principal) {

        if (request != null
                && request.refreshToken() != null
                && !request.refreshToken().isBlank()) {
            authService.logout(request.refreshToken());
            return ApiResponse.success("Đăng xuất thành công");
        }

        if (principal != null) {
            try {
                authService.logoutAll(Long.valueOf(principal.getName()));
            } catch (final NumberFormatException ex) {
                // No usable principal: the local session is cleared regardless,
                // so there is nothing further to do server-side.
            }
        }

        return ApiResponse.success("Đăng xuất thành công");
    }

    /** Current principal; re-validates a persisted session on client boot. */
    @GetMapping("/me")
    public ApiResponse<UserProfileDto> me(final Principal principal) {
        if (principal == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        final Long userId;
        try {
            userId = Long.valueOf(principal.getName());
        } catch (final NumberFormatException ex) {
            // The subject is set to the user id by JwtService; anything else
            // means the token was minted by something other than this service.
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
        return ApiResponse.success(authService.currentUser(userId), "Lấy thông tin tài khoản thành công");
    }
}