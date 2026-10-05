package com.thesis.elearning.modules.exam.service.grading;

import static com.thesis.elearning.modules.exam.service.grading.GradingStrategy.ratio;
import static com.thesis.elearning.modules.exam.service.grading.GradingStrategy.readStringMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.thesis.elearning.modules.question.domain.QuestionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;

/**
 * Marks a FILL_IN_BLANK answer per blank, with proportional partial credit.
 *
 * <h2>The denominator is the key, not the student's submission</h2>
 * Marks are {@code points * correctBlanks / totalBlanks}, where {@code
 * totalBlanks} comes from {@code acceptedAnswers} - the blanks the teacher
 * defined. Iterating the student's map instead would let a student who filled
 * in one of five blanks score 100% by submitting only that one.
 *
 * <h2>Author-supplied regexes are treated as untrusted input</h2>
 * {@code patterns} lets a teacher accept a family of answers. A malformed or
 * catastrophically backtracking pattern is therefore compiled defensively and
 * degrades to a literal comparison rather than propagating a
 * {@link PatternSyntaxException} out of a grading pass and losing the whole
 * paper. The teacher gets a question that grades more strictly, which is the
 * safe direction to fail in.
 */
@Component
public class FillInBlankGradingStrategy implements GradingStrategy {

    private static final String ACCEPTED_ANSWERS = "acceptedAnswers";
    private static final String PATTERNS = "patterns";
    private static final String ANSWERS = "answers";

    @Override
    public QuestionType supports() {
        return QuestionType.FILL_IN_BLANK;
    }

    @Override
    public GradingResult grade(
            final JsonNode solutionPayload,
            final JsonNode studentResponse,
            final BigDecimal points) {

        final Map<String, List<String>> accepted = readAcceptedAnswers(solutionPayload);
        if (accepted.isEmpty()) {
            return GradingResult.unanswered();
        }
        final Map<String, String> given = readStringMap(studentResponse, ANSWERS);
        final Map<String, String> patterns = readStringMap(solutionPayload, PATTERNS);

        boolean anyAnswered = false;
        int correct = 0;
        for (final Map.Entry<String, List<String>> blank : accepted.entrySet()) {
            final String answer = given.get(blank.getKey());
            if (answer == null || AnswerNormalizer.normalize(answer).isEmpty()) {
                continue;
            }
            anyAnswered = true;
            if (matches(blank.getValue(), patterns.get(blank.getKey()), answer)) {
                correct++;
            }
        }

        if (!anyAnswered) {
            return GradingResult.unanswered();
        }
        final BigDecimal earned = ratio(points, correct, accepted.size());
        return earned.compareTo(BigDecimal.ZERO) == 0
                ? GradingResult.wrong()
                : GradingResult.partial(earned, points);
    }

    /** {@code acceptedAnswers} is a map of blank id to the list of spellings. */
    private Map<String, List<String>> readAcceptedAnswers(final JsonNode solutionPayload) {
        final JsonNode node = solutionPayload == null ? null : solutionPayload.get(ACCEPTED_ANSWERS);
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        final Map<String, List<String>> out = new java.util.LinkedHashMap<>();
        node.fields()
                .forEachRemaining(
                        entry -> {
                            final List<String> spellings = toStringList(entry.getValue());
                            if (!spellings.isEmpty()) {
                                out.put(entry.getKey(), spellings);
                            }
                        });
        return out;
    }

    private static List<String> toStringList(final JsonNode node) {
        final List<String> out = new java.util.ArrayList<>();
        if (node != null && node.isArray()) {
            for (final JsonNode element : node) {
                if (element.isTextual()) {
                    out.add(element.asText());
                }
            }
        }
        return out;
    }

    /**
     * Whether one typed answer satisfies a blank.
     *
     * <p>A registered pattern wins when it compiles, since it is the teacher's
     * stated intent to accept a family of forms; otherwise the literal
     * comparison runs.
     */
    private boolean matches(
            final List<String> accepted, final String pattern, final String answer) {
        final String normalisedAnswer = AnswerNormalizer.normalize(answer);

        if (pattern != null && !pattern.isBlank()) {
            try {
                if (Pattern.compile(pattern).matcher(normalisedAnswer).matches()) {
                    return true;
                }
            } catch (final PatternSyntaxException ex) {
                // Fall through to the literal comparison below.
            }
        }
        for (final String candidate : accepted) {
            if (AnswerNormalizer.equivalent(answer, candidate)) {
                return true;
            }
        }
        return false;
    }
}
