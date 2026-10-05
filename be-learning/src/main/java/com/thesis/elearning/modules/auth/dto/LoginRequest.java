package com.thesis.elearning.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sign-in body.
 *
 * <p>Matches {@code LoginRequest} in {@code src/types/auth.ts} on the client.
 * The single field is labelled "Tên đăng nhập hoặc email" in the UI and the
 * server resolves either identifier, so the name stays {@code username} for
 * wire compatibility.
 *
 * @param username account name or email address.
 * @param password plaintext password, compared against the stored BCrypt hash.
 */
public record LoginRequest(
        @NotBlank(message = "Vui lòng nhập tên đăng nhập hoặc email")
                @Size(max = 100, message = "Tên đăng nhập không được vượt quá 100 ký tự")
                String username,

        @NotBlank(message = "Vui lòng nhập mật khẩu")
                @Size(max = 72, message = "Mật khẩu không được vượt quá 72 ký tự")
                String password) {}