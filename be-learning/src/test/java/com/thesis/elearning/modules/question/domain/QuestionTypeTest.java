package com.thesis.elearning.modules.question.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class QuestionTypeTest {

    @ParameterizedTest
    @EnumSource(QuestionType.class)
    @DisplayName("Every supported type resolves to itself")
    void resolvesEverySupportedType(final QuestionType type) {
        assertThat(QuestionType.require(type.name())).isEqualTo(type);
    }

    @ParameterizedTest
    @EnumSource(QuestionType.class)
    @DisplayName("isSupported agrees with require")
    void isSupportedAgreesWithRequire(final QuestionType type) {
        assertThat(QuestionType.isSupported(type.name())).isTrue();
    }

    @ParameterizedTest
    @EnumSource(QuestionType.class)
    @DisplayName("Resolution is case-insensitive and trims surrounding space")
    void resolutionIsForgiving(final QuestionType type) {
        assertThat(QuestionType.require("  " + type.name().toLowerCase(java.util.Locale.ROOT) + " "))
                .isEqualTo(type);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ESSAY", "TRUE_FALSE", "multiple choice", "MCQ", "DRAG_DROP"})
    @DisplayName("An unsupported type is rejected with the Vietnamese message naming it")
    void rejectsUnsupportedType(final String raw) {
        assertThatThrownBy(() -> QuestionType.require(raw))
                .isInstanceOf(AppException.class)
                .hasMessage("Loại câu hỏi không được hỗ trợ trong hệ thống: " + raw + ".")
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        assertThat(QuestionType.isSupported(raw)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Null and blank are rejected rather than silently defaulting")
    void rejectsNullAndBlank(final String raw) {
        assertThatThrownBy(() -> QuestionType.require(raw))
                .isInstanceOf(AppException.class)
                .hasMessageStartingWith("Loại câu hỏi không được hỗ trợ trong hệ thống:");

        assertThat(QuestionType.isSupported(raw)).isFalse();
    }

    @Test
    @DisplayName("isSupported never throws, including for null")
    void isSupportedNeverThrows() {
        assertThatCode(() -> QuestionType.isSupported(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Exactly the four renderers the client supports are accepted")
    void exposesExactlyFourTypes() {
        assertThat(QuestionType.values())
                .containsExactly(
                        QuestionType.MULTIPLE_CHOICE,
                        QuestionType.FILL_IN_BLANK,
                        QuestionType.MATCHING,
                        QuestionType.ORDERING);
    }
}
