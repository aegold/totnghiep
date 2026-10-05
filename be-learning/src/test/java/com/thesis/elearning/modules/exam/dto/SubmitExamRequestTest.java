package com.thesis.elearning.modules.exam.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The proctoring reconciliation rule and blank-paper normalisation.
 *
 * <p>Pure unit tests: both behaviours are decided in the DTO so the rule has one
 * home rather than being re-derived by the service, the analytics report and the
 * client in turn.
 */
class SubmitExamRequestTest {

    private static SubmitExamRequest request(final Integer tabs, final Integer violations) {
        return new SubmitExamRequest(1L, List.of(), tabs, violations, List.of(), null, false);
    }

    @Test
    @DisplayName("an omitted answers array is normalised to empty, never left null")
    void nullAnswersBecomeEmpty() {
        assertThat(new SubmitExamRequest(1L, null, 0, 0, null, null, false).answers()).isEmpty();
    }

    @Test
    @DisplayName("a blank paper is accepted, not rejected")
    void blankPaperIsValid() {
        assertThat(request(0, 0).answers()).isEmpty();
    }

    @Test
    @DisplayName("tabSwitchCount wins when both spellings are present")
    void primaryNameWins() {
        assertThat(request(5, 2).resolvedTabSwitchCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("a client sending only the older violationCount is not recorded as clean")
    void fallsBackToLegacyName() {
        assertThat(request(0, 4).resolvedTabSwitchCount()).isEqualTo(4);
        assertThat(request(null, 4).resolvedTabSwitchCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("an absent counter on both names resolves to zero, not a null")
    void absentCountIsZero() {
        assertThat(request(null, null).resolvedTabSwitchCount()).isZero();
        assertThat(request(0, 0).resolvedTabSwitchCount()).isZero();
    }

    @Test
    @DisplayName("a genuine zero alongside a real legacy count still reports the count")
    void zeroDoesNotMaskARealSignal() {
        assertThat(request(0, 7).resolvedTabSwitchCount()).isEqualTo(7);
    }
}