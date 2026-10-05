package com.thesis.elearning.modules.question.domain;

import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import java.util.Locale;
import java.util.Set;

/**
 * The four renderers the question engine can display.
 *
 * <p>Mirrors the client's {@code QuestionType} enum exactly. An author may only
 * pick a member of this set; anything else is rejected at write time rather than
 * stored and discovered at render time, when the failure would surface to a
 * student sitting an exam rather than to the teacher who made the mistake.
 */
public enum QuestionType {

    /** Single or multi select over authored options. */
    MULTIPLE_CHOICE,

    /** Free text scored against accepted answers, case-insensitively. */
    FILL_IN_BLANK,

    /** Pair each left-hand item with a right-hand item. */
    MATCHING,

    /** Reorder authored items into an authored sequence. */
    ORDERING;

    private static final Set<String> SUPPORTED =
            Set.of(MULTIPLE_CHOICE.name(), FILL_IN_BLANK.name(), MATCHING.name(), ORDERING.name());

    /**
     * Resolves a stored or submitted discriminator, or rejects it.
     *
     * <p>Normalised to upper case so a client sending {@code multiple_choice} is
     * accepted; the comparison is case-insensitive but the value is not trimmed of
     * internal characters, because a type name is a closed vocabulary rather than
     * free text and silently repairing near-misses hides typos.
     *
     * @param raw value as received.
     * @return the matching constant.
     * @throws AppException 400 with {@code Loại câu hỏi không được hỗ trợ trong hệ thống: {type}.}
     *                      when the value is null, blank, or outside the set.
     */
    public static QuestionType require(final String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, unsupportedMessage(String.valueOf(raw)));
        }
        final String normalised = raw.trim().toUpperCase(Locale.ROOT);
        for (final QuestionType type : values()) {
            if (type.name().equals(normalised)) {
                return type;
            }
        }
        throw new AppException(ErrorCode.BAD_REQUEST, unsupportedMessage(raw));
    }

    /** Whether {@code raw} names a supported renderer, without throwing. */
    public static boolean isSupported(final String raw) {
        return raw != null && SUPPORTED.contains(raw.trim().toUpperCase(Locale.ROOT));
    }

    private static String unsupportedMessage(final String raw) {
        return "Loại câu hỏi không được hỗ trợ trong hệ thống: " + raw + ".";
    }
}
