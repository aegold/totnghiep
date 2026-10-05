package com.thesis.elearning.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public self-registration body.
 *
 * ## Why there is no role field
 * An earlier draft of this DTO carried a {@code requestedRole} field. That is a
 * direct privilege-escalation hole (CWE-269): anyone could POST
 * {@code requestedRole: "ROLE_ADMIN"} and be granted administrator rights on a
 * freshly created account. Self-registration therefore always yields
 * {@code ROLE_STUDENT}.
 *
 * <p>Teachers and administrators are provisioned by an existing administrator
 * through {@code POST /admin/users}, which is behind {@code hasRole('ADMIN')}.
 * Keep it that way: any field that lets a caller choose their own authority must
 * never appear on a public endpoint.
 */
public record RegisterRequest(
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
                String fullName) {}