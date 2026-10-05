package com.thesis.elearning.modules.exam.service.grading;

import java.math.BigDecimal;

/**
 * What one strategy decided about one answer.
 *
 * @param earnedPoints  marks awarded; never negative and never above the
 *                      question's own {@code points}.
 * @param fullyCorrect  true only when {@code earnedPoints} equals the full
 *                      value. Kept distinct from "answered" so a question
 *                      allowing partial credit can still record that nothing
 *                      was wrong - {@code is_correct} on {@code
 *                      submission_answers} carries this flag, and a marker sheet
 *                      reading it should see "not a mistake" rather than "wrong".
 * @param answered      whether the student supplied anything at all. Drives the
 *                      unanswered count and is distinct from scoring zero: a
 *                      blank answer and a wrong answer are both zero marks but
 *                      are different facts about the attempt.
 */
public record GradingResult(BigDecimal earnedPoints, boolean fullyCorrect, boolean answered) {

    /** Nothing was supplied; zero marks. */
    public static GradingResult unanswered() {
        return new GradingResult(BigDecimal.ZERO, false, false);
    }

    /** An answer was supplied and was entirely right. */
    public static GradingResult full(final BigDecimal points) {
        return new GradingResult(points, true, true);
    }

    /**
     * An answer was supplied and earned nothing.
     *
     * <p>Kept distinct from {@link #unanswered()}: the student tried and was
     * wrong, which a report needs to show differently from a blank page.
     */
    public static GradingResult wrong() {
        return new GradingResult(BigDecimal.ZERO, false, true);
    }

    /**
     * Partial credit.
     *
     * @param earned marks awarded, strictly between zero and the full value.
     * @param points the question's full value, used to decide full correctness.
     */
    public static GradingResult partial(final BigDecimal earned, final BigDecimal points) {
        return new GradingResult(
                earned, earned.compareTo(points) >= 0, true);
    }
}
