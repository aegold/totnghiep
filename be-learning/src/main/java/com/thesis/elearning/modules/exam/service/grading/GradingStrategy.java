package com.thesis.elearning.modules.exam.service.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.thesis.elearning.modules.question.domain.QuestionType;
import java.math.BigDecimal;

/**
 * Marks one answer against one question's answer key.
 *
 * <h2>Why the strategy only ever sees {@code solution_payload}</h2>
 * The signature has no parameter for {@code content_payload}: what the student
 * sees and what marks it are different documents, and a grader that could read
 * the prompt would be one refactor away from serialising either. The answer key
 * alone is sufficient to score every renderer, so the prompt is simply not
 * reachable from here.
 *
 * <h2>Contract for implementers</h2>
 * Implementations must not throw on a malformed or absent student response. A
 * student who submits garbage, or nothing, has still sat the exam; the correct
 * outcome is zero marks, not a 500 that loses their whole paper. Unknown or
 * unparseable input is scored as unanswered or wrong.
 *
 * <p>Implementations must never return more than {@code points}, nor a negative
 * amount, so a partial-credit ratio can be trusted by the caller.
 */
public interface GradingStrategy {

    /** The renderer this strategy grades. */
    QuestionType supports();

    /**
     * Scores one answer.
     *
     * @param solutionPayload  the question's answer key; never {@code null}.
     * @param studentResponse  what the student submitted; may be {@code null} or
     *                         structurally wrong.
     * @param points           the marks this question carries in its paper.
     * @return the outcome, never {@code null}.
     */
    GradingResult grade(JsonNode solutionPayload, JsonNode studentResponse, BigDecimal points);

    /* ------------------------------------------------------------------ */
    /*  Helpers shared by the implementations                              */
    /* ------------------------------------------------------------------ */

    /**
     * Reads a field as an array of strings, tolerating absence.
     *
     * <p>Returns an empty list rather than {@code null} so strategies can iterate
     * unconditionally instead of each re-deriving the same defensive shape.
     */
    static java.util.List<String> readStringArray(final JsonNode node, final String field) {
        final JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isArray()) {
            return java.util.List.of();
        }
        final java.util.List<String> out = new java.util.ArrayList<>();
        for (final JsonNode element : value) {
            if (element.isTextual()) {
                out.add(element.asText());
            }
        }
        return out;
    }

    /** Reads a string-to-string object field, tolerating absence. */
    static java.util.Map<String, String> readStringMap(final JsonNode node, final String field) {
        final JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isObject()) {
            return java.util.Map.of();
        }
        final java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        value.fields()
                .forEachRemaining(
                        entry -> {
                            if (entry.getValue().isTextual()) {
                                out.put(entry.getKey(), entry.getValue().asText());
                            }
                        });
        return out;
    }

    /** Scales {@code points} by a correct/total ratio, to the column's scale. */
    static BigDecimal ratio(final BigDecimal points, final int correct, final int total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return points
                .multiply(BigDecimal.valueOf(correct))
                .divide(BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP);
    }
}
