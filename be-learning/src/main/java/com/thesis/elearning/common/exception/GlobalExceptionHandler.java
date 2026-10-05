package com.thesis.elearning.common.exception;

import com.thesis.elearning.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Single translation point from exceptions to HTTP responses.
 *
 * <p>Every branch emits the {@link ApiResponse} envelope so the React client can
 * read {@code message} and {@code fieldErrors} without special-casing a status
 * code. Log messages stay in English (they are for operators reading a server
 * log); only the response body carries Vietnamese.
 *
 * <p>Handling order matters: the most specific exception types are declared
 * first so Spring can match on the concrete type rather than a supertype.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Correlation header echoed back so a user-reported error is traceable. */
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    /**
     * Maps a property path to a Vietnamese label.
     *
     * <p>Explicit rather than derived: a machine transform of {@code username}
     * cannot produce "Tên đăng nhập", so every label a form can send is listed.
     */
    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("username", "Tên đăng nhập"),
            Map.entry("password", "Mật khẩu"),
            Map.entry("email", "Địa chỉ email"),
            Map.entry("fullName", "Họ và tên"),
            Map.entry("confirmPassword", "Xác nhận mật khẩu"),
            Map.entry("refreshToken", "Token làm mới"),
            Map.entry("questionType", "Loại câu hỏi"),
            Map.entry("prompt", "Nội dung câu hỏi"),
            Map.entry("hint", "Gợi ý"),
            Map.entry("options", "Phương án"),
            Map.entry("blanks", "Ô trống"),
            Map.entry("items", "Mục"),
            Map.entry("leftItems", "Mục bên trái"),
            Map.entry("rightItems", "Mục bên phải"),
            Map.entry("pairs", "Cặp nối"),
            Map.entry("correctOrder", "Thứ tự đúng"),
            Map.entry("title", "Tiêu đề"),
            Map.entry("description", "Mô tả"),
            Map.entry("page", "Trang"),
            Map.entry("size", "Số bản ghi mỗi trang"),
            Map.entry("sort", "Tiêu chí sắp xếp"));

    /** Human-readable fallback when a field is not in {@link #FIELD_LABELS}. */
    private static String labelFor(final String field) {
        if (field == null || field.isBlank()) {
            return "Dữ liệu";
        }
        // Strip a trailing array index, e.g. "options[0]" -> "options".
        final String base = field.replaceAll("\\[\\d+]$", "");
        return FIELD_LABELS.getOrDefault(base, base);
    }

    /** Resolves the correlation id for an unexpected failure. */
    private static String correlationIdFor(final HttpServletRequest request) {
        final String header = request.getHeader(CORRELATION_HEADER);
        if (header != null && !header.isBlank()) {
            return header;
        }
        return "req-" + Long.toHexString(System.nanoTime());
    }

    /**
     * Expected application failure.
     *
     * <p>Rendered at {@code WARN} without a stack trace: the code already
     * describes the situation, and these are business outcomes, not defects.
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(
            final AppException ex, final HttpServletRequest request) {

        final ErrorCode code = ex.getErrorCode();
        log.warn(
                "Business exception on {} {} -> [{}] {}",
                request.getMethod(),
                request.getRequestURI(),
                code.code(),
                ex.getMessage());

        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(ex.getMessage(), code.code()));
    }

    /**
     * Bean-validation failure on a request body.
     *
     * <p>Each rejected field becomes its own entry so the client can render the
     * message next to the offending input rather than in a single banner.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            final MethodArgumentNotValidException ex) {

        final Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (final FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            final String key = fieldError.getField();
            // First message wins: it is the most specific constraint that failed.
            fieldErrors.putIfAbsent(
                    key, "[" + labelFor(key) + "] " + fieldError.getDefaultMessage());
        }

        log.warn("Validation failed on {} field(s): {}", fieldErrors.size(), fieldErrors.keySet());

        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus())
                .body(ApiResponse.<Void>error(
                        ErrorCode.VALIDATION_FAILED.defaultMessage(),
                        ErrorCode.VALIDATION_FAILED.code(),
                        fieldErrors));
    }

    /** Authenticated principal lacks the required authority. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(final AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(ErrorCode.FORBIDDEN.httpStatus())
                .body(ApiResponse.error(
                        ErrorCode.FORBIDDEN.defaultMessage(), ErrorCode.FORBIDDEN.code()));
    }

    /** Authentication failure raised inside the filter chain, not a controller. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(
            final AuthenticationException ex) {
        // Logged by type only: AuthenticationException messages can echo credentials.
        log.warn("Authentication failed: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.httpStatus())
                .body(ApiResponse.error(
                        ErrorCode.UNAUTHORIZED.defaultMessage(), ErrorCode.UNAUTHORIZED.code()));
    }

    /** Unknown route under the servlet context path. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(final NoResourceFoundException ex) {
        log.warn("No handler for {} {}", ex.getHttpMethod(), ex.getResourcePath());
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.httpStatus())
                .body(ApiResponse.error(
                        ErrorCode.RESOURCE_NOT_FOUND.defaultMessage(),
                        ErrorCode.RESOURCE_NOT_FOUND.code()));
    }

    /**
     * Anything unanticipated.
     *
     * <p>The full stack trace is logged server-side for diagnosis, but the
     * response body stays generic: an unexpected exception's message frequently
     * embeds SQL, class names, or file paths, none of which belong in an API
     * response. Each failure carries a correlation id so a user-reported error
     * can be traced back to its log line.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            final Exception ex, final HttpServletRequest request) {

        final String correlationId = correlationIdFor(request);

        log.error(
                "Unhandled exception [{}] on {} {}",
                correlationId,
                request.getMethod(),
                request.getRequestURI(),
                ex);

        final String message = ErrorCode.INTERNAL_SERVER_ERROR.defaultMessage()
                + " (Mã tra cứu: " + correlationId + ")";

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(CORRELATION_HEADER, correlationId)
                .body(ApiResponse.error(message, ErrorCode.INTERNAL_SERVER_ERROR.code()));
    }
}