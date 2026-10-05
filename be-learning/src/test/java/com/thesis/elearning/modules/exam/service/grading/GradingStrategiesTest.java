package com.thesis.elearning.modules.exam.service.grading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thesis.elearning.modules.question.domain.QuestionType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Scoring rules for all four renderers.
 *
 * <p>Each renderer has its own strategy, but the contract they share is the part
 * worth pinning: marks never exceed the question's value, never go negative, a
 * blank is distinguishable from a wrong answer, and {@code fullyCorrect} means
 * full marks rather than "not zero". Those hold for every type and are asserted
 * together in {@link SharedContract}.
 */
class GradingStrategiesTest {

    private static final BigDecimal TEN = new BigDecimal("10.00");
    private static final BigDecimal FOUR = new BigDecimal("4.00");

    private final ObjectMapper mapper = new ObjectMapper();
    private final GradingStrategyFactory factory =
            new GradingStrategyFactory(
                    List.of(
                            new MultipleChoiceGradingStrategy(),
                            new FillInBlankGradingStrategy(),
                            new MatchingGradingStrategy(),
                            new OrderingGradingStrategy()));

    private JsonNode json(final String raw) {
        try {
            return mapper.readTree(raw);
        } catch (final Exception ex) {
            throw new AssertionError("fixture is not valid JSON: " + raw, ex);
        }
    }

    private GradingResult grade(
            final QuestionType type,
            final String solution,
            final String response,
            final BigDecimal points) {
        return factory.forType(type)
                .grade(json(solution), "null".equals(response) ? null : json(response), points);
    }

    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("MULTIPLE_CHOICE")
    class MultipleChoice {

        @Test
        @DisplayName("Exact selection earns full marks")
        void exactSelection() {
            final var result =
                    grade(
                            QuestionType.MULTIPLE_CHOICE,
                            "{\"correctOptionIds\":[\"a\"]}",
                            "{\"selectedOptionIds\":[\"a\"]}",
                            TEN);
            assertThat(result.earnedPoints()).isEqualByComparingTo(TEN);
            assertThat(result.fullyCorrect()).isTrue();
        }

        @Test
        @DisplayName("Multi-select must match the key exactly, in any tick order")
        void multiSelectExact() {
            assertThat(
                            grade(
                                            QuestionType.MULTIPLE_CHOICE,
                                            "{\"correctOptionIds\":[\"a\",\"c\"]}",
                                            "{\"selectedOptionIds\":[\"a\",\"c\"]}",
                                            TEN)
                                    .fullyCorrect())
                    .isTrue();
            assertThat(
                            grade(
                                            QuestionType.MULTIPLE_CHOICE,
                                            "{\"correctOptionIds\":[\"a\",\"c\"]}",
                                            "{\"selectedOptionIds\":[\"c\",\"a\"]}",
                                            TEN)
                                    .fullyCorrect())
                    .isTrue();
        }

        @Test
        @DisplayName("An extra wrong tick scores zero, not partial credit")
        void extraSelectionScoresZero() {
            final var result =
                    grade(
                            QuestionType.MULTIPLE_CHOICE,
                            "{\"correctOptionIds\":[\"a\"]}",
                            "{\"selectedOptionIds\":[\"a\",\"b\"]}",
                            TEN);
            assertThat(result.earnedPoints()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.answered()).isTrue();
            assertThat(result.fullyCorrect()).isFalse();
        }

        @Test
        @DisplayName("A missing right tick scores zero")
        void missingSelectionScoresZero() {
            assertThat(
                            grade(
                                            QuestionType.MULTIPLE_CHOICE,
                                            "{\"correctOptionIds\":[\"a\",\"b\"]}",
                                            "{\"selectedOptionIds\":[\"a\"]}",
                                            TEN)
                                    .earnedPoints())
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("An empty selection is unanswered, not wrong")
        void emptySelectionIsUnanswered() {
            final var result =
                    grade(
                            QuestionType.MULTIPLE_CHOICE,
                            "{\"correctOptionIds\":[\"a\"]}",
                            "{\"selectedOptionIds\":[]}",
                            TEN);
            assertThat(result.answered()).isFalse();
            assertThat(result.earnedPoints()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("FILL_IN_BLANK (partial credit)")
    class FillInBlank {

        private static final String SOLUTION =
                "{\"acceptedAnswers\":{\"b1\":[\"Số tự nhiên\"],\"b2\":[\"vô hạn\"],"
                        + "\"b3\":[\"0\"],\"b4\":[\"chia hết\"]}}";

        @Test
        @DisplayName("All blanks right earns full marks, however they are spelled")
        void allCorrect() {
            final var result =
                    grade(
                            QuestionType.FILL_IN_BLANK,
                            SOLUTION,
                            "{\"answers\":{\"b1\":\"so tu nhien\",\"b2\":\"VO HAN\","
                                    + "\"b3\":\"0\",\"b4\":\"  chia het  \"}}",
                            TEN);
            assertThat(result.earnedPoints()).isEqualByComparingTo(TEN);
            assertThat(result.fullyCorrect()).isTrue();
        }

        @Test
        @DisplayName("Half the blanks right earns half the marks")
        void halfCorrectEarnsHalf() {
            final var result =
                    grade(
                            QuestionType.FILL_IN_BLANK,
                            SOLUTION,
                            "{\"answers\":{\"b1\":\"số tự nhiên\",\"b2\":\"hữu hạn\","
                                    + "\"b3\":\"0\",\"b4\":\"không\"}}",
                            TEN);
            // 2 of 4 blanks.
            assertThat(result.earnedPoints()).isEqualByComparingTo("5.00");
            assertThat(result.fullyCorrect()).isFalse();
            assertThat(result.answered()).isTrue();
        }

        @Test
        @DisplayName("A third correct on a 4-point question rounds to two decimals")
        void thirdCorrectRounds() {
            final var result =
                    grade(
                            QuestionType.FILL_IN_BLANK,
                            "{\"acceptedAnswers\":{\"b1\":[\"a\"],\"b2\":[\"b\"],\"b3\":[\"c\"]}}",
                            "{\"answers\":{\"b1\":\"a\",\"b2\":\"x\",\"b3\":\"y\"}}",
                            FOUR);
            assertThat(result.earnedPoints()).isEqualByComparingTo("1.33");
        }

        @Test
        @DisplayName("The denominator is the key, so one of four blanks cannot score full marks")
        void denominatorComesFromTheKey() {
            final var result =
                    grade(
                            QuestionType.FILL_IN_BLANK,
                            SOLUTION,
                            "{\"answers\":{\"b1\":\"số tự nhiên\"}}",
                            TEN);
            assertThat(result.earnedPoints()).isEqualByComparingTo("2.50");
            assertThat(result.fullyCorrect()).isFalse();
        }

        @Test
        @DisplayName("Any accepted spelling satisfies a blank")
        void anyAcceptedSpellingCounts() {
            assertThat(
                            grade(
                                            QuestionType.FILL_IN_BLANK,
                                            "{\"acceptedAnswers\":{\"b1\":[\"không\",\"khong\"]}}",
                                            "{\"answers\":{\"b1\":\"KHÔNG\"}}",
                                            TEN)
                                    .fullyCorrect())
                    .isTrue();
        }

        @Test
        @DisplayName("Whitespace-only blanks count as unanswered, not wrong")
        void blankIsUnanswered() {
            final var result =
                    grade(
                            QuestionType.FILL_IN_BLANK,
                            SOLUTION,
                            "{\"answers\":{\"b1\":\"   \",\"b2\":\"\",\"b3\":\"\",\"b4\":\"\"}}",
                            TEN);
            assertThat(result.answered()).isFalse();
            assertThat(result.earnedPoints()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("A registered regex accepts a family of answers")
        void regexPatternAccepts() {
            assertThat(
                            grade(
                                            QuestionType.FILL_IN_BLANK,
                                            "{\"acceptedAnswers\":{\"b1\":[\"x\"]},"
                                                    + "\"patterns\":{\"b1\":\"[0-9]+\"}}",
                                            "{\"answers\":{\"b1\":\"12345\"}}",
                                            TEN)
                                    .fullyCorrect())
                    .isTrue();
        }

        @Test
        @DisplayName("A malformed regex degrades to a literal check instead of throwing")
        void malformedRegexDoesNotBreakGrading() {
            assertThat(
                            grade(
                                            QuestionType.FILL_IN_BLANK,
                                            "{\"acceptedAnswers\":{\"b1\":[\"hai muoi\"]},"
                                                    + "\"patterns\":{\"b1\":\"[unclosed\"}}",
                                            "{\"answers\":{\"b1\":\"hai muoi\"}}",
                                            TEN)
                                    .fullyCorrect())
                    .isTrue();
        }
    }


    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("MATCHING (partial credit)")
    class Matching {

        private static final String SOLUTION =
                "{\"correctPairs\":{\"l1\":\"r_a\",\"l2\":\"r_b\",\"l3\":\"r_c\",\"l4\":\"r_d\"}}";

        @Test
        @DisplayName("All pairs right earns full marks")
        void allCorrect() {
            final var result =
                    grade(
                            QuestionType.MATCHING,
                            SOLUTION,
                            "{\"pairs\":{\"l1\":\"r_a\",\"l2\":\"r_b\",\"l3\":\"r_c\",\"l4\":\"r_d\"}}",
                            TEN);
            assertThat(result.earnedPoints()).isEqualByComparingTo(TEN);
            assertThat(result.fullyCorrect()).isTrue();
        }

        @Test
        @DisplayName("Three of four pairs right earns three quarters")
        void threeOfFour() {
            final var result =
                    grade(
                            QuestionType.MATCHING,
                            SOLUTION,
                            "{\"pairs\":{\"l1\":\"r_a\",\"l2\":\"r_b\",\"l3\":\"r_c\",\"l4\":\"r_b\"}}",
                            TEN);
            assertThat(result.earnedPoints()).isEqualByComparingTo("7.50");
            assertThat(result.fullyCorrect()).isFalse();
        }

        @Test
        @DisplayName("One wrong pair costs exactly that pair's share")
        void wrongPairCostsItsShare() {
            final var result =
                    grade(
                            QuestionType.MATCHING,
                            SOLUTION,
                            "{\"pairs\":{\"l1\":\"r_b\",\"l2\":\"r_b\",\"l3\":\"r_c\",\"l4\":\"r_d\"}}",
                            TEN);
            assertThat(result.earnedPoints()).isEqualByComparingTo("7.50");
            assertThat(result.answered()).isTrue();
        }

        @Test
        @DisplayName("An unpaired left item does not inflate the ratio")
        void unpairedItemIsNotFree() {
            final var result =
                    grade(
                            QuestionType.MATCHING, SOLUTION, "{\"pairs\":{\"l1\":\"r_a\"}}", TEN);
            // 1 of 4, not 1 of 1.
            assertThat(result.earnedPoints()).isEqualByComparingTo("2.50");
        }

        @Test
        @DisplayName("No pairs at all is unanswered")
        void noPairsIsUnanswered() {
            assertThat(grade(QuestionType.MATCHING, SOLUTION, "{\"pairs\":{}}", TEN).answered())
                    .isFalse();
        }
    }

    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("ORDERING")
    class Ordering {

        private static final String SOLUTION = "{\"correctOrder\":[\"i1\",\"i2\",\"i3\"]}";

        @Test
        @DisplayName("The exact sequence earns full marks")
        void exactOrder() {
            final var result =
                    grade(
                            QuestionType.ORDERING,
                            SOLUTION,
                            "{\"submittedOrder\":[\"i1\",\"i2\",\"i3\"]}",
                            TEN);
            assertThat(result.fullyCorrect()).isTrue();
            assertThat(result.earnedPoints()).isEqualByComparingTo(TEN);
        }

        @Test
        @DisplayName("A single swap scores zero, with no partial credit")
        void singleSwapScoresZero() {
            final var result =
                    grade(
                            QuestionType.ORDERING,
                            SOLUTION,
                            "{\"submittedOrder\":[\"i1\",\"i3\",\"i2\"]}",
                            TEN);
            assertThat(result.earnedPoints()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.answered()).isTrue();
            assertThat(result.fullyCorrect()).isFalse();
        }

        @Test
        @DisplayName("A dropped or duplicated item scores zero")
        void malformedSequenceScoresZero() {
            assertThat(
                            grade(
                                            QuestionType.ORDERING,
                                            SOLUTION,
                                            "{\"submittedOrder\":[\"i1\",\"i2\"]}",
                                            TEN)
                                    .earnedPoints())
                    .isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(
                            grade(
                                            QuestionType.ORDERING,
                                            SOLUTION,
                                            "{\"submittedOrder\":[\"i1\",\"i1\",\"i2\"]}",
                                            TEN)
                                    .earnedPoints())
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("An empty order is unanswered")
        void emptyOrderIsUnanswered() {
            assertThat(
                            grade(
                                            QuestionType.ORDERING,
                                            SOLUTION,
                                            "{\"submittedOrder\":[]}",
                                            TEN)
                                    .answered())
                    .isFalse();
        }
    }


    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("Shared contract")
    class SharedContract {

        /** A key and a matching answer for each renderer. */
        private String keyFor(final QuestionType type) {
            return switch (type) {
                case MULTIPLE_CHOICE -> "{\"correctOptionIds\":[\"a\"]}";
                case FILL_IN_BLANK -> "{\"acceptedAnswers\":{\"b1\":[\"a\"]}}";
                case MATCHING -> "{\"correctPairs\":{\"l1\":\"r1\"}}";
                case ORDERING -> "{\"correctOrder\":[\"i1\"]}";
            };
        }

        private String hitFor(final QuestionType type) {
            return switch (type) {
                case MULTIPLE_CHOICE -> "{\"selectedOptionIds\":[\"a\"]}";
                case FILL_IN_BLANK -> "{\"answers\":{\"b1\":\"a\"}}";
                case MATCHING -> "{\"pairs\":{\"l1\":\"r1\"}}";
                case ORDERING -> "{\"submittedOrder\":[\"i1\"]}";
            };
        }

        @Test
        @DisplayName("Malformed or absent input never throws, for any renderer")
        void malformedInputIsSafe() {
            final String[][] samples = {
                {"null", "\"answers\":\"not-an-object\""},
                {"{}", "{\"pairs\":[1,2,3]}"},
                {"{\"correctOrder\":\"nope\"}", "{\"submittedOrder\":42}"},
            };
            for (final QuestionType type : QuestionType.values()) {
                for (final String[] sample : samples) {
                    final JsonNode solution = "null".equals(sample[0]) ? null : json(sample[0]);
                    assertThatCode(
                                    () -> factory.forType(type).grade(solution, json(sample[1]), TEN))
                            .as("%s must survive %s / %s", type, sample[0], sample[1])
                            .doesNotThrowAnyException();
                }
            }
        }

        @Test
        @DisplayName("A null response scores zero and counts as unanswered")
        void nullResponseIsUnanswered() {
            for (final QuestionType type : QuestionType.values()) {
                final GradingResult result = factory.forType(type).grade(json(keyFor(type)), null, TEN);
                assertThat(result.answered()).as("%s answered", type).isFalse();
                assertThat(result.earnedPoints()).as("%s marks", type)
                        .isEqualByComparingTo(BigDecimal.ZERO);
            }
        }

        @Test
        @DisplayName("Marks never exceed the question's value and never go negative")
        void marksStayInRange() {
            for (final QuestionType type : QuestionType.values()) {
                final GradingResult result =
                        factory.forType(type).grade(json(keyFor(type)), json(hitFor(type)), TEN);
                assertThat(result.earnedPoints()).as("%s upper bound", type)
                        .isLessThanOrEqualTo(TEN);
                assertThat(result.earnedPoints()).as("%s lower bound", type)
                        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
                assertThat(result.fullyCorrect()).as("%s full marks", type).isTrue();
            }
        }

        @Test
        @DisplayName("The factory registers exactly one strategy per renderer")
        void factoryCoversEveryType() {
            assertThat(factory.size()).isEqualTo(QuestionType.values().length);
            for (final QuestionType type : QuestionType.values()) {
                assertThat(factory.forType(type).supports()).isEqualTo(type);
            }
        }

        @Test
        @DisplayName("A duplicate strategy for one type is refused at construction")
        void duplicateRegistrationIsRefused() {
            assertThatThrownBy(
                            () ->
                                    new GradingStrategyFactory(
                                            List.of(
                                                    new MultipleChoiceGradingStrategy(),
                                                    new MultipleChoiceGradingStrategy())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MULTIPLE_CHOICE");
        }

        @Test
        @DisplayName("A missing strategy for one type is refused at construction")
        void missingRegistrationIsRefused() {
            assertThatThrownBy(
                            () ->
                                    new GradingStrategyFactory(
                                            List.of(new MultipleChoiceGradingStrategy())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Thiếu chiến lược");
        }
    }
}

