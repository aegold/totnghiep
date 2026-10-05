package com.thesis.elearning.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified response envelope returned by every endpoint.
 *
 * <p>Shape: {@code { success, data, message, timestamp, errorCode? }}.
 *
 * <p>## Why the shape is fixed
 * The React client unwraps exactly this contract in {@code apiClient.ts}: it reads
 * {@code message} and {@code fieldErrors} off the failure body to populate the
 * global alert banner and per-field errors. Any endpoint that returns a bare
 * payload on error makes those two reads silently undefined.
 *
 * @param success      {@code true} for 2xx, {@code false} for every error status.
 * @param data         Payload on success; {@code null} on error.
 * @param message      Human-facing Vietnamese text, safe to render directly.
 * @param timestamp    Server-side emission time, ISO-8601 with an explicit offset.
 * @param errorCode    Stable machine-readable code; {@code null} on success.
 * @param fieldErrors  Property path to Vietnamese message. Empty on success.
 *                     Omitted from JSON when empty (see {@link JsonInclude}).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        Instant timestamp,
        String errorCode,
        Map<String, String> fieldErrors) {

    /** Canonical success factory. */
    public static <T> ApiResponse<T> success(final T data, final String message) {
        return new ApiResponse<>(true, data, message, Instant.now(), null, Map.of());
    }

    /** Success factory for endpoints that carry no meaningful payload. */
    public static ApiResponse<Void> success(final String message) {
        return new ApiResponse<>(true, null, message, Instant.now(), null, Map.of());
    }

    /** Failure factory with a stable error code. */
    public static <T> ApiResponse<T> error(
            final String message, final String errorCode, final Map<String, String> fieldErrors) {
        return new ApiResponse<>(
                false,
                null,
                message,
                Instant.now(),
                errorCode,
                fieldErrors == null || fieldErrors.isEmpty()
                        ? Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors)));
    }

    /** Failure factory without field-level detail. */
    public static <T> ApiResponse<T> error(final String message, final String errorCode) {
        return error(message, errorCode, Map.of());
    }

    /**
     * Read-only view for callers that only need the payload.
     *
     * <p>Kept as an explicit accessor so a null payload cannot be dereferenced
     * by accident on the success path.
     */
    public T requireData() {
        if (data == null) {
            throw new IllegalStateException(
                    "ApiResponse carries no payload (message: " + message + ")");
        }
        return data;
    }
}