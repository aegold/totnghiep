package com.thesis.elearning.common.exception;

import java.io.Serial;

/**
 * Application-level failure carrying a catalogue {@link ErrorCode}.
 *
 * <p>Thrown from services and repositories to express an expected outcome
 * (missing row, rejected credentials, duplicate account) without coupling the
 * caller to Spring's exception types. {@link GlobalExceptionHandler} maps it
 * onto the HTTP status declared by the code.
 *
 * <p>The {@link #message} may be a more specific Vietnamese phrasing than the
 * code's default - for example naming the field that failed uniqueness - but it
 * must never contain a stack trace, a SQL fragment, or a class name.
 */
public class AppException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient ErrorCode errorCode;

    public AppException(final ErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * @param message Vietnamese text overriding the code's default wording.
     */
    public AppException(final ErrorCode errorCode, final String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AppException(final ErrorCode errorCode, final String message, final Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** Convenience factories keeping call sites terse and intention-revealing. */
    public static AppException notFound() {
        return new AppException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    public static AppException badRequest(final String message) {
        return new AppException(ErrorCode.BAD_REQUEST, message);
    }

    public static AppException unauthorized(final String message) {
        return new AppException(ErrorCode.UNAUTHORIZED, message);
    }

    public static AppException forbidden() {
        return new AppException(ErrorCode.FORBIDDEN);
    }
}