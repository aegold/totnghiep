package com.thesis.elearning.modules.auth.dto;

/**
 * The issued token pair.
 *
 * <p>Nested under {@code tokens} because that is exactly where the client reads
 * it: {@code const { user, tokens } = await authApi.login(...)} in
 * {@code useAuth.ts}. Flattening this would make {@code tokens} undefined and
 * the client would silently render a signed-out shell.
 *
 * @param accessToken short-lived JWT used as {@code Authorization: Bearer}.
 * @param refreshToken opaque value used only against {@code /auth/refresh}.
 */
public record AuthTokens(String accessToken, String refreshToken) {}