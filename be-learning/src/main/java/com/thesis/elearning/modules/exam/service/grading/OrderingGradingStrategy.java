package com.thesis.elearning.modules.exam.service.grading;

import static com.thesis.elearning.modules.exam.service.grading.GradingStrategy.readStringArray;

import com.fasterxml.jackson.databind.JsonNode;
import com.thesis.elearning.modules.question.domain.QuestionType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Marks an ORDERING answer, all or nothing.
 *
 * <h2>Why sequencing gets no partial credit</h2>
 * The other two partial-credit types are made of independent parts, where
 * "how much was right" has an obvious denominator. A sequence is a single
 * ordered fact: in a TCP handshake, SYN-ACK-FIN is not three quarters of the
 * answer, it is a different answer. Awarding a fraction of an ordering question
 * would also need an adjacency metric - how far is this sequence from the
 * right one - which the payload does not specify and which teachers would
 * reasonably grade differently from each other.
 *
 * <p>Comparison is on ids, in order. Text is never consulted, so re-wording an
 * item's label cannot break an already-marked paper.
 */
@Component
public class OrderingGradingStrategy implements GradingStrategy {

    private static final String CORRECT_ORDER = "correctOrder";
    private static final String SUBMITTED_ORDER = "submittedOrder";

    @Override
    public QuestionType supports() {
        return QuestionType.ORDERING;
    }

    @Override
    public GradingResult grade(
            final JsonNode solutionPayload,
            final JsonNode studentResponse,
            final BigDecimal points) {

        final List<String> submitted = readStringArray(studentResponse, SUBMITTED_ORDER);
        if (submitted.isEmpty()) {
            return GradingResult.unanswered();
        }
        final List<String> key = readStringArray(solutionPayload, CORRECT_ORDER);

        // List.equals is ordered equality, which is exactly the requirement:
        // duplicates and omissions both make the lists unequal.
        return submitted.equals(key) ? GradingResult.full(points) : GradingResult.wrong();
    }
}
