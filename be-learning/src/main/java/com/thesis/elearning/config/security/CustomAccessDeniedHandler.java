package com.thesis.elearning.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.elearning.common.dto.ApiResponse;
import com.thesis.elearning.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Renders 403 responses for authenticated-but-unauthorised requests.
 *
 * <p>Distinct from the 401 entry point on purpose: the caller proved who they
 * are, they simply lack the authority for this resource. The distinction matters
 * to the client, which must not try to refresh a session that will never
 * succeed.
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final AccessDeniedException accessDeniedException)
            throws IOException {

        response.setStatus(ErrorCode.FORBIDDEN.statusValue());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.error(
                        ErrorCode.FORBIDDEN.defaultMessage(), ErrorCode.FORBIDDEN.code()));
    }
}