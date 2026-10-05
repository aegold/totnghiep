package com.thesis.elearning.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.thesis.elearning.common.dto.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the error surface.
 *
 * <p>These assert the two properties the React client actually depends on:
 * every failure carries the envelope, and every default message is Vietnamese
 * rather than an internal string.
 */
class GlobalExceptionHandlerTest {

    /**
     * Every codepoint Vietnamese orthography can require: Latin-1 Supplement
     * ({@code à á ả ã ạ} style letters), the {@code đ Đ ơ Ơ ư Ư} letters, and
     * Latin Extended Additional ({@code ộ ệ ữ ị} and friends).
     */
    private static final Pattern VIETNAMESE_CHARACTER =
            Pattern.compile("[À-ÿĀ-ſƠơƯưẠ-ỹ]");

    @Nested
    @DisplayName("ErrorCode catalogue")
    class ErrorCodeContract {

        @Test
        @DisplayName("exposes every code required by the specification with its HTTP status")
        void exposesSpecifiedCodes() {
            assertThat(ErrorCode.UNAUTHORIZED.statusValue()).isEqualTo(401);
            assertThat(ErrorCode.FORBIDDEN.statusValue()).isEqualTo(403);
            assertThat(ErrorCode.RESOURCE_NOT_FOUND.statusValue()).isEqualTo(404);
            assertThat(ErrorCode.BAD_REQUEST.statusValue()).isEqualTo(400);
            assertThat(ErrorCode.INTERNAL_SERVER_ERROR.statusValue()).isEqualTo(500);
        }

        @Test
        @DisplayName("every default message is non-blank and written in Vietnamese")
        void allDefaultMessagesAreVietnamese() {
            for (final ErrorCode code : ErrorCode.values()) {
                final String message = code.defaultMessage();
                assertThat(message)
                        .as("default message for %s", code)
                        .isNotBlank();

                /*
                 * Vietnamese orthography needs characters outside ASCII that an
                 * English sentence can never contain. Matching the full set of
                 * Vietnamese blocks catches an untranslated string without
                 * hard-coding a hand-picked sample (which silently misses
                 * precomposed forms such as U+1ED1 "ộ").
                 */
                final boolean containsVietnameseCharacter =
                        VIETNAMESE_CHARACTER.matcher(message).find();

                assertThat(containsVietnameseCharacter)
                        .as("default message for %s looks untranslated: %s", code, message)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("code() matches the enum constant name so clients can switch on it")
        void codeMatchesName() {
            for (final ErrorCode code : ErrorCode.values()) {
                assertThat(code.code()).isEqualTo(code.name());
            }
        }
    }

    @Nested
    @DisplayName("ApiResponse envelope")
    class ApiResponseContract {

        @Test
        @DisplayName("success carries the payload, a message and no error code")
        void successShape() {
            final ApiResponse<String> response = ApiResponse.success("CS201", "Thành công");

            assertThat(response.success()).isTrue();
            assertThat(response.data()).isEqualTo("CS201");
            assertThat(response.message()).isEqualTo("Thành công");
            assertThat(response.timestamp()).isNotNull();
            assertThat(response.errorCode()).isNull();
            assertThat(response.fieldErrors()).isEmpty();
            assertThat(response.requireData()).isEqualTo("CS201");
        }

        @Test
        @DisplayName("error carries no payload but does carry a code")
        void errorShape() {
            final ApiResponse<Void> response =
                    ApiResponse.error("Không tìm thấy dữ liệu yêu cầu", "RESOURCE_NOT_FOUND");

            assertThat(response.success()).isFalse();
            assertThat(response.data()).isNull();
            assertThat(response.errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        }

        @Test
        @DisplayName("field errors are preserved and exposed for per-field rendering")
        void fieldErrorsSurvive() {
            final Map<String, String> fieldErrors =
                    Map.of("username", "[Tên đăng nhập] không được để trống");

            final ApiResponse<Void> response =
                    ApiResponse.error("Dữ liệu yêu cầu không hợp lệ", "VALIDATION_FAILED", fieldErrors);

            assertThat(response.fieldErrors()).containsEntry("username", "[Tên đăng nhập] không được để trống");
        }

        @Test
        @DisplayName("requireData refuses to hand back a null payload")
        void requireDataRejectsNull() {
            final ApiResponse<Void> response = ApiResponse.error("Lỗi", "BAD_REQUEST");

            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, response::requireData);
        }
    }

    @Nested
    @DisplayName("AppException")
    class AppExceptionContract {

        @Test
        @DisplayName("falls back to the catalogue default message")
        void usesDefaultMessage() {
            final AppException ex = new AppException(ErrorCode.FORBIDDEN);

            assertThat(ex.getMessage()).isEqualTo("Bạn không có quyền truy cập vào tài nguyên này");
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("honours a caller-supplied Vietnamese override")
        void honoursOverride() {
            final AppException ex = new AppException(ErrorCode.INVALID_CREDENTIALS, "Mật khẩu không đúng");

            assertThat(ex.getMessage()).isEqualTo("Mật khẩu không đúng");
        }
    }

    /** Demonstrates the constraint text a controller would carry. */
    record LoginForm(@NotBlank String username, @NotBlank String password) {}
}