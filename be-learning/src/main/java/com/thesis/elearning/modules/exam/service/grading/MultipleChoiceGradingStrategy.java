package com.thesis.elearning.modules.exam.service.grading;

import static com.thesis.elearning.modules.exam.service.grading.GradingStrategy.ratio;
import static com.thesis.elearning.modules.exam.service.grading.GradingStrategy.readStringArray;

import com.fasterxml.jackson.databind.JsonNode;
import com.thesis.elearning.modules.question.domain.QuestionType;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Marks a MULTIPLE_CHOICE answer, all or nothing.
 *
 * <h2>Why no partial credit here</h2>
 * Both single-select and multi-select are decided by set equality against the
 * key. Partial credit would mean deciding what an extra wrong option costs
 * relative to a missing right one, which is a policy question the payload does
 * not answer - and for single-select there is nothing to be partial about. The
 * question types that do carry partial credit are the ones made of independent
 * parts (blanks, pairs), where "how much was right" has an obvious denominator.
 */
@Component
public class MultipleChoiceGradingStrategy implements GradingStrategy {

    private static final String CORRECT_OPTION_IDS = "correctOptionIds";
    private static final String SELECTED_OPTION_IDS = "selectedOptionIds";

    @Override
    public QuestionType supports() {
        return QuestionType.MULTIPLE_CHOICE;
    }

    @Override
    public GradingResult grade(
            final JsonNode solutionPayload,
            final JsonNode studentResponse,
            final BigDecimal points) {

        final List<String> selected = readStringArray(studentResponse, SELECTED_OPTION_IDS);
        if (selected.isEmpty()) {
            return GradingResult.unanswered();
        }

        final Set<String> chosen = new HashSet<>(selected);
        final Set<String> correct = new HashSet<>(readStringArray(solutionPayload, CORRECT_OPTION_IDS));

        // Exact set equality, so an extra tick costs the whole question just as a
        // missing one does.
        return chosen.equals(correct) ? GradingResult.full(points) : GradingResult.wrong();
    }
}
