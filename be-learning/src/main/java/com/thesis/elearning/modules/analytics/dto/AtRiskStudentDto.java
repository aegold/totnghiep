package com.thesis.elearning.modules.analytics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * A student flagged for intervention, with the reason spelled out.
 *
 * @param studentId     the {@code users.user_id}.
 * @param fullName      display name, as stored.
 * @param email         contact address, so the dashboard can offer a route to
 *                      the student without a second lookup.
 * @param averageScore  mean score as a percentage, or {@code null} when the
 *                      student has no submitted attempt - possible when the only
 *                      trigger was a proctoring strike on work that was never
 *                      handed in.
 * @param maxTabSwitches highest strike count in any single attempt, not the
 *                      total. Ten switches spread over four papers is ordinary
 *                      coursework; three in one sitting is a different signal,
 *                      and averaging would hide it.
 * @param riskReason    Vietnamese sentence naming every trigger that fired.
 */
public record AtRiskStudentDto(
        Long studentId,
        String fullName,
        String email,
        BigDecimal averageScore,
        int maxTabSwitches,
        String riskReason) {

    /**
     * The same figure under the name the dashboard already reads.
     *
     * <p>The column is {@code tab_switch_count}, so the field describes the
     * column exactly and is named for what it is; the dashboard's table, written
     * against an earlier mock, calls it {@code tabSwitchCount}. Rather than break
     * one consumer or rename a well-named field to suit a mock, both names are
     * emitted. The explicit {@link JsonProperty} is what makes this reliable -
     * relying on Jackson's bean-naming convention would not, because record
     * accessors are not {@code getX()} methods.
     *
     * <p>Additive, so the payload carries one redundant number rather than the
     * client having to branch on which server it is talking to.
     */
    @JsonProperty("tabSwitchCount")
    public int tabSwitchCount() {
        return maxTabSwitches;
    }
}