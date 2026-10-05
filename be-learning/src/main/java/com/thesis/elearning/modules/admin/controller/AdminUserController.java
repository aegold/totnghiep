package com.thesis.elearning.modules.admin.controller;

import com.thesis.elearning.common.dto.ApiResponse;
import com.thesis.elearning.modules.admin.service.AdminUserService;
import com.thesis.elearning.modules.auth.dto.UserProfileDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator-only account management.
 *
 * <p>Guarded twice on purpose. The path matcher in {@code SecurityConfig} already
 * restricts {@code /admin/**} to {@code ROLE_ADMIN}; the
 * {@link PreAuthorize} annotations repeat the requirement at the method so a
 * future controller mounted outside that subtree cannot inherit the protection by
 * accident.
 *
 * <p>This endpoint exists because public self-registration is intentionally
 * unable to grant roles.
 */
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(final AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /** Authorities an administrator may assign, for populating a picker. */
    @GetMapping("/roles")
    public ApiResponse<List<String>> assignableRoles() {
        return ApiResponse.success(
                List.of("ROLE_STUDENT", "ROLE_TEACHER", "ROLE_ADMIN"),
                "Lấy danh sách vai trò thành công");
    }

    /**
     * Creates an account with an explicit role.
     *
     * <p>The {@code role} field exists only here, behind {@code hasRole('ADMIN')}.
     * It must never be added to the public registration DTO.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<UserProfileDto>> create(
            @Valid @RequestBody final CreateUserRequest request) {

        final UserProfileDto created =
                adminUserService.createUser(
                        request.username(),
                        request.email(),
                        request.password(),
                        request.fullName(),
                        request.role());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        ApiResponse.success(
                                created, "Tạo tài khoản thành công với vai trò " + request.role()));
    }

    /** Enables or disables an account. */
    @PatchMapping("/{userId}/status")
    public ApiResponse<UserProfileDto> setStatus(
            @PathVariable final Long userId,
            @RequestParam(defaultValue = "true") final boolean active) {

        return ApiResponse.success(
                adminUserService.setActive(userId, active),
                active ? "Đã kích hoạt tài khoản" : "Đã vô hiệu hóa tài khoản");
    }

    /**
     * Admin-only creation body.
     *
     * <p>Kept as a nested record because it is the one DTO in the system that is
     * allowed to carry a role, and it should be visually obvious in the code when
     * that is true.
     */
    public record CreateUserRequest(
            @NotBlank(message = "Vui lòng nhập tên đăng nhập")
                    @Pattern(
                            regexp = "^[a-zA-Z0-9._-]{3,50}$",
                            message = "Tên đăng nhập phải dài 3-50 ký tự và chỉ gồm chữ, số, dấu chấm, gạch dưới hoặc gạch ngang")
                    String username,

            @NotBlank(message = "Vui lòng nhập địa chỉ email")
                    @Email(message = "Địa chỉ email không hợp lệ")
                    @Size(max = 100, message = "Địa chỉ email không được vượt quá 100 ký tự")
                    String email,

            @NotBlank(message = "Vui lòng nhập mật khẩu")
                    @Size(min = 8, max = 72, message = "Mật khẩu phải dài từ 8 đến 72 ký tự")
                    String password,

            @NotBlank(message = "Vui lòng nhập họ và tên")
                    @Size(max = 100, message = "Họ và tên không được vượt quá 100 ký tự")
                    String fullName,

            @NotBlank(message = "Vui lòng chọn vai trò")
                    @Pattern(
                            regexp = "ROLE_(STUDENT|TEACHER|ADMIN)",
                            message = "Vai trò chỉ được là ROLE_STUDENT, ROLE_TEACHER hoặc ROLE_ADMIN")
                    String role) {}
}