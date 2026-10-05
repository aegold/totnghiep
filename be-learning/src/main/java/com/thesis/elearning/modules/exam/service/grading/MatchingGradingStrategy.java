package com.thesis.elearning.modules.exam.service.grading;

import static com.thesis.elearning.modules.exam.service.grading.GradingStrategy.ratio;
import static com.thesis.elearning.modules.exam.service.grading.GradingStrategy.readStringMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.thesis.elearning.modules.question.domain.QuestionType;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Marks a MATCHING answer per pair, with proportional partial credit.
 *
 * <h2>Partial credit is the whole point of this renderer</h2>
 * Matching is the one type where the answer decomposes into independent,
 * individually meaningful units. A student who pairs seven of eight correctly
 * knows most of the material, and an all-or-nothing mark would report that as a
 * total failure. Marks are {@code points * correctPairs / totalPairs}, with the
 * denominator taken from {@code correctPairs} - the teacher's key - so a
 * partially filled form cannot inflate the ratio.
 *
 * <p>An unpaired left item counts as an attempt that failed: it is neither
 * correct nor silent, so it must not inflate the mark.
 */
@Component
public class MatchingGradingStrategy implements GradingStrategy {

    private static final String CORRECT_PAIRS = "correctPairs";
    private static final String PAIRS = "pairs";

    @Override
    public QuestionType supports() {
        return QuestionType.MATCHING;
    }

    @Override
    public GradingResult grade(
            final JsonNode solutionPayload,
            final JsonNode studentResponse,
            final BigDecimal points) {

        final Map<String, String> key = readStringMap(solutionPayload, CORRECT_PAIRS);
        if (key.isEmpty()) {
            return GradingResult.unanswered();
        }
        final Map<String, String> given = readStringMap(studentResponse, PAIRS);

        boolean anyAnswered = false;
        int correct = 0;
        for (final Map.Entry<String, String> pair : key.entrySet()) {
            final String chosen = given.get(pair.getKey());
            if (chosen == null || chosen.isBlank()) {
                continue;
            }
            anyAnswered = true;
            // Ids are opaque strings chosen by the teacher, so they are compared
            // exactly - normalising them would risk folding two distinct ids
            // such as "a-1" and "a 1" into one another.
            if (chosen.equals(pair.getValue())) {
                correct++;
            }
        }

        if (!anyAnswered) {
            return GradingResult.unanswered();
        }
        final BigDecimal earned = ratio(points, correct, key.size());
        return earned.compareTo(BigDecimal.ZERO) == 0
                ? GradingResult.wrong()
                : GradingResult.partial(earned, points);
    }
}
