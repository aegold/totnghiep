package com.thesis.elearning.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Silent-renewal body.
 *
 * <p>The token is an opaque server-side value, not a JWT: it must be revocable,
 * which a self-contained signed token cannot be. Only its SHA-256 digest is
 * stored, so a leaked database row yields no usable credential.
 *
 * @param refreshToken the raw token previously issued to this client.
 */
public record RefreshTokenRequest(
        @NotBlank(message = "Vui lòng cung cấp mã làm mới") String refreshToken) {}