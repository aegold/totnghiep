package com.thesis.elearning.modules.analytics.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * When a student is flagged as needing support, and how that is explained.
 *
 * <h2>Why the triggers are OR-ed</h2>
 * The two populations fail independently: a strong scorer who kept switching tabs
 * may be looking up answers, and a weak scorer with a spotless session may simply
 * be struggling. Requiring both would miss both populations - which is exactly
 * the population a teacher needs to see.
 *
 * <h2>Why the threshold is a percentage, not a raw mark</h2>
 * Papers are not all worth ten marks, so comparing raw totals across attempts of
 * different papers would rank 7/20 above 9/10. The database therefore yields
 * percentages and the comparison happens on those; {@link ScoreBucket#toTenPointScale}
 * is applied only when rendering, keeping the arithmetic scale-free while the
 * Vietnamese text stays familiar.
 *
 * <p>Membership itself is decided by a SQL {@code HAVING} clause - the rows that
 * arrive are already at-risk students. This class decides only the <em>wording</em>,
 * which SQL cannot compose without a {@code CASE} that restates the thresholds
 * as string literals and would drift from these.
 */
public final class AtRiskRule {

    /** Average score, as a percentage, below which a student is at risk. */
    public static final BigDecimal SCORE_THRESHOLD = BigDecimal.valueOf(50);

    /** Proctoring strikes in any single attempt at or above which a student is at risk. */
    public static final int TAB_SWITCH_THRESHOLD = 3;

    private AtRiskRule() {}

    /** Whether either trigger fires. Mirrors the query's {@code HAVING}. */
    public static boolean isAtRisk(final BigDecimal averageScore, final int maxTabSwitches) {
        return isLowScoring(averageScore) || isProctored(maxTabSwitches);
    }

    /** {@code < 50}: exactly 50.0% passes, matching the {@code HAVING} clause. */
    public static boolean isLowScoring(final BigDecimal averageScore) {
        return averageScore == null || averageScore.compareTo(SCORE_THRESHOLD) < 0;
    }

    /** {@code >= 3}: the count that triggers on the third strike, not the fourth. */
    public static boolean isProctored(final int maxTabSwitches) {
        return maxTabSwitches >= TAB_SWITCH_THRESHOLD;
    }

    /**
     * Builds the Vietnamese explanation shown to the teacher.
     *
     * <p>Every triggered clause is included, so a student failing both ways reads
     * as both instead of being described by whichever test happened to run first.
     * A student at exactly 50.0% produces no score clause, so a proctored-only
     * student is never also told they are failing.
     *
     * @param averageScore mean percentage across the student's submissions.
     * @param maxTabSwitches highest strike count in any single attempt.
     * @return a sentence naming every trigger that fired; empty when none did.
     */
    public static String reason(final BigDecimal averageScore, final int maxTabSwitches) {
        final List<String> causes = new ArrayList<>(2);

        if (isLowScoring(averageScore)) {
            causes.add(
                    "Điểm trung bình dưới "
                            + ScoreBucket.toTenPointScale(SCORE_THRESHOLD)
                            + " ("
                            + ScoreBucket.toTenPointScale(averageScore)
                            + "/10)");
        }
        if (isProctored(maxTabSwitches)) {
            causes.add("vi phạm rời tab " + maxTabSwitches + " lần");
        }

        if (causes.isEmpty()) {
            return "";
        }
        // Capitalise the leading clause so the two forms read as one sentence
        // regardless of which trigger fired first.
        final String first = causes.get(0);
        final StringBuilder sentence = new StringBuilder(causes.size() * 2 + 40);
        sentence.append(Character.toUpperCase(first.charAt(0))).append(first, 1, first.length());
        for (int i = 1; i < causes.size(); i++) {
            sentence.append(i == 1 ? " và " : ", ").append(causes.get(i));
        }
        return sentence.append('.').toString();
    }
}