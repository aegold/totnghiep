package com.thesis.elearning.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Catalogue of every failure the API can report.
 *
 * <p>Each member couples an HTTP status, a stable machine-readable code, and a
 * default Vietnamese message. Adding a failure mode means adding a member here
 * rather than throwing a bare {@code RuntimeException} with an inline string,
 * which keeps the Vietnamese wording consistent and lets the client branch on
 * {@code errorCode} instead of parsing prose.
 *
 * <p>The default message is rendered verbatim whenever a caller does not supply
 * a more specific one, so every message in this enum must stay user-facing and
 * free of internal identifiers.
 */
public enum ErrorCode {

    /** Credentials missing, malformed, or the access token has expired. */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Yêu cầu xác thực tài khoản để tiếp tục"),

    /** Authenticated, but the role is not permitted to perform the operation. */
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập vào tài nguyên này"),

    /** No row matches the supplied identifier. */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu yêu cầu"),

    /** Syntactically valid request whose semantics are rejected. */
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ"),

    /** Bean-validation failure on one or more request fields. */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu yêu cầu không hợp lệ"),

    /** Credentials supplied but rejected. */
    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không chính xác."),

    /** Username or email already taken. */
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Tài khoản đã tồn tại"),

    /**
     * Refresh token expired, was revoked, or failed signature validation.
     *
     * <p>Deliberately one code for all three: distinguishing them would let an
     * attacker probe which refresh tokens exist.
     */
    INVALID_REFRESH_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "Mã làm mới không hợp lệ hoặc phiên đăng nhập đã bị thu hồi."),

    /**
     * The token signature or structure is wrong.
     *
     * <p>Separate from {@link #ACCESS_TOKEN_EXPIRED} so the client can tell a
     * tampered token from a merely stale one; the Vietnamese text differs but
     * both statuses are 401.
     */
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Mã truy cập không hợp lệ."),

    /**
     * The access token verified, but its expiry has passed.
     *
     * <p>Surfaced as a distinct message so the learner is told to renew the
     * session rather than to sign in again from scratch.
     */
    ACCESS_TOKEN_EXPIRED(
            HttpStatus.UNAUTHORIZED,
            "Mã truy cập đã hết hạn, vui lòng làm mới phiên đăng nhập."),

    /**
     * The principal authenticated, but the account itself is not in good standing.
     *
     * <p>403 rather than 401: renewing the token cannot fix a disabled account,
     * so the client must NOT loop the refresh interceptor. Only ever returned
     * AFTER the password has been verified, otherwise it becomes an
     * account-existence oracle.
     */
    ACCOUNT_DISABLED(
            HttpStatus.FORBIDDEN, "Tài khoản của bạn đã bị vô hiệu hóa hoặc chưa kích hoạt."),

    /** Unhandled failure; never leaks internals to the caller. */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Hệ thống gặp sự cố, vui lòng thử lại sau");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(final HttpStatus httpStatus, final String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    /** HTTP status to answer with. */
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    /** Numeric status, for convenience at the advice layer. */
    public int statusValue() {
        return httpStatus.value();
    }

    /** Stable client-facing identifier; identical to the enum constant name. */
    public String code() {
        return name();
    }

    /** Default Vietnamese wording, used when no override is supplied. */
    public String defaultMessage() {
        return defaultMessage;
    }
}