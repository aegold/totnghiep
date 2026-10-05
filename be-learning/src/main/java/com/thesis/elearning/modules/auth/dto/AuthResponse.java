package com.thesis.elearning.modules.auth.dto;

import java.time.Instant;

/**
 * Body returned by login, register and refresh.
 *
 * <p>## Why this stays nested
 * The React client destructures the result as
 * {@code const { user, tokens } = await authApi.login(...)} and hands
 * {@code tokens} straight to {@code setAuth}. A flat shape
 * ({@code accessToken, refreshToken, user}) would leave {@code tokens}
 * {@code undefined}; the auth store would derive {@code isAuthenticated =
 * false} and the UI would render a signed-out shell with no error anywhere. The
 * nesting is a wire contract, not a style choice.
 *
 * <p>{@code tokenType} and {@code expiresIn} are additive. RFC 6750 defines
 * them, and a client that ignores unknown fields keeps working, so they cost
 * nothing while making the payload self-describing for curl and Postman.
 *
 * @param user       principal, safe to persist client-side.
 * @param tokens     the pair; the refresh token is single-use and rotated.
 * @param expiresAt  absolute ISO-8601 expiry of the access token.
 * @param tokenType  always {@code Bearer}, per RFC 6750.
 * @param expiresIn  access-token lifetime in seconds.
 */
public record AuthResponse(
        UserProfileDto user,
        AuthTokens tokens,
        Instant expiresAt,
        String tokenType,
        long expiresIn) {

    /** RFC 6750 token type identifier. */
    public static final String BEARER = "Bearer";
}