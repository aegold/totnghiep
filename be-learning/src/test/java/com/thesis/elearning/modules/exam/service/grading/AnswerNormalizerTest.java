package com.thesis.elearning.modules.exam.service.grading;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Text canonicalisation, with the Vietnamese cases called out explicitly.
 *
 * <p>These are the rules that decide whether a Vietnamese student is marked
 * correctly, so each {@code đ}-bearing case is its own test: the failure mode of
 * stripping diacritics without folding the stroked D is silent and would mark
 * an entire class of students wrong on every fill-in-blank question.
 */
class AnswerNormalizerTest {

    @ParameterizedTest(name = "[{index}] ''{0}'' and ''{1}'' are the same")
    @CsvSource({
        // The stroked D: U+0111 has no NFD decomposition, so these only match
        // when the explicit d-fold runs before mark removal.
        "Định nghĩa, định nghĩa",
        "ĐẠNG SỐ, đang so",
        "định, Dinh",
        "Đ, d",
        // Ordinary diacritics, which NFD does decompose.
        "Tích phân, tich phan",
        "Hàm số, ham so",
        "đại số tuyến tính, dai so tuyen tinh",
        // Case and whitespace are not the student's fault.
        "Set, set",
        "SET, set",
        "'  ba   đầu  ', 'ba đầu'",
        "hai   ba, hai ba",
        "'\tphân số\n', phân số",
        // Compatibility forms.
        "ｆ, f",
    })
    @DisplayName("Equivalent spellings collapse to one form")
    void equivalentSpellingsCollapse(final String left, final String right) {
        assertThat(AnswerNormalizer.equivalent(left, right)).isTrue();
    }

    @ParameterizedTest(name = "[{index}] ''{0}'' and ''{1}'' differ")
    @CsvSource({
        "abc, abd",
        "hai, hain",
        "1, 2",
        // Folding đ to d must not collapse genuinely different words.
        "một, hai",
        "đ, đô",
        "đất, cát",
    })
    @DisplayName("Genuinely different answers stay different")
    void differentAnswersStayDifferent(final String left, final String right) {
        assertThat(AnswerNormalizer.equivalent(left, right)).isFalse();
    }

    @Test
    @DisplayName("Null and blank both normalise to the empty string, not to null")
    void nullAndBlankBecomeEmpty() {
        assertThat(AnswerNormalizer.normalize(null)).isEmpty();
        assertThat(AnswerNormalizer.normalize("")).isEmpty();
        assertThat(AnswerNormalizer.normalize("   ")).isEmpty();
        assertThat(AnswerNormalizer.normalize("\t\n ")).isEmpty();
    }

    @Test
    @DisplayName("A whitespace-only answer matches a blank, not a letter")
    void whitespaceIsNotAnAnswer() {
        assertThat(AnswerNormalizer.equivalent("  ", "")).isTrue();
        assertThat(AnswerNormalizer.equivalent("  ", "a")).isFalse();
    }

    @Test
    @DisplayName("Normalisation is idempotent, so re-normalising changes nothing")
    void normalisationIsIdempotent() {
        final String once = AnswerNormalizer.normalize("  ĐỊNH   NGHĨA  ");
        assertThat(AnswerNormalizer.normalize(once)).isEqualTo(once);
        assertThat(once).isEqualTo("dinh nghia");
    }

    @Test
    @DisplayName("Preserves digits and mathematical punctuation")
    void keepsDigitsAndSymbols() {
        assertThat(AnswerNormalizer.normalize("f(x) = 2x + 1")).isEqualTo("f(x) = 2x + 1");
        assertThat(AnswerNormalizer.normalize("x^2 ≥ 0")).isEqualTo("x^2 ≥ 0");
    }
}
