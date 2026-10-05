package com.thesis.elearning.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.elearning.common.dto.ApiResponse;
import com.thesis.elearning.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Renders 401 responses for unauthenticated requests.
 *
 * <p>Reached from the security filter chain, where
 * {@code @RestControllerAdvice} does not apply, so the body is written by hand.
 * It must match the {@link ApiResponse} envelope exactly: the client reads
 * {@code message} and {@code errorCode} off any failure, and a bare Spring error
 * page would leave the login form with nothing to display.
 *
 * <p>The message is taken from the attribute the JWT filter stored, which is what
 * lets an expired token read "your session needs renewing" instead of the generic
 * "authentication required" - a materially different remedy for the user.
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public CustomAuthenticationEntryPoint(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final AuthenticationException authException)
            throws IOException {

        // Prefer the precise reason recorded by the filter; fall back to the
        // generic 401 when no token was presented at all.
        final ErrorCode code =
                JwtAuthenticationFilter.failureOf(request) != null
                        ? JwtAuthenticationFilter.failureOf(request)
                        : ErrorCode.UNAUTHORIZED;

        response.setStatus(code.statusValue());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(
                response.getWriter(), ApiResponse.error(code.defaultMessage(), code.code()));
    }
}