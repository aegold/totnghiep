package com.thesis.elearning.modules.exam.service.grading;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Canonical form used to compare a typed answer against an accepted one.
 *
 * <h2>Why this exists at all</h2>
 * A student typing {@code "Định nghĩa"} and a teacher writing {@code "định
 * nghĩa"} mean the same word, and marking the first wrong would be indefensible
 * in a Vietnamese classroom. Comparison therefore ignores case, surrounding and
 * repeated whitespace, and diacritics.
 *
 * <h2>The {@code đ} trap</h2>
 * Vietnamese tone marks and vowels are combining marks, so NFD splits them off
 * and they can be stripped mechanically. {@code đ} is not: it is a distinct
 * letter (LATIN SMALL LETTER D WITH STROKE, U+0111) with no decomposition, so
 * {@code value.normalize("NFD").replace("\\p{Diacritic}", "")} silently leaves
 * {@code đ} intact. Stripping diacritics first and folding {@code đ -> d} is
 * therefore the only order that works, and it is why this class exists rather
 * than a three-line regex at each call site.
 */
public final class AnswerNormalizer {

    /**
     * Folds the stroked D before any Unicode work.
     *
     * <p>Both cases, because the upper-case form U+0110 does not lower-case into
     * the stroked form and vice versa.
     */
    private static final Pattern STROKED_D = Pattern.compile("[đĐ]");

    /** Combining marks left behind by NFD. */
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private AnswerNormalizer() {
        throw new AssertionError("Utility class");
    }

    /**
     * Reduces a free-text answer to its comparison form.
     *
     * <p>Order matters and is not interchangeable: compatibility folding first
     * so full-width or ligature variants collapse; {@code đ} replacement next,
     * while it is still a distinct code point; NFD and mark removal after, since
     * replacement above reintroduced no combining marks; then case and
     * whitespace.
     *
     * @param raw any input, including {@code null}.
     * @return the normalised form, never {@code null}; {@code null} in gives
     *         {@code ""} out so callers need no null guard.
     */
    public static String normalize(final String raw) {
        if (raw == null) {
            return "";
        }
        final String compatibilityFolded = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        final String dFolded = STROKED_D.matcher(compatibilityFolded).replaceAll("d");

        // Lower-case before decomposing so an upper-case base letter and its
        // lower-case combining form cannot diverge.
        final String decomposed = Normalizer.normalize(dFolded.toLowerCase(java.util.Locale.ROOT),
                Normalizer.Form.NFD);
        final String unmarked = COMBINING_MARKS.matcher(decomposed).replaceAll("");

        return WHITESPACE_RUN.matcher(unmarked).replaceAll(" ").trim();
    }

    /** Whether two answers are equivalent once normalised. */
    public static boolean equivalent(final String left, final String right) {
        return normalize(left).equals(normalize(right));
    }
}
